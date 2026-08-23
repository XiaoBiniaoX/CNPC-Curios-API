package top.bincnpc.cnpccurios;

import net.minecraft.world.entity.EntityType;
import noppes.npcs.entity.EntityNPCInterface;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.common.data.CuriosEntityManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC 饰品槽位继承逻辑：让 CNPC 的 NPC 自动拥有「玩家拥有的全部槽位」。
 *
 * 本类刻意放在**普通业务包**（top.bincnpc.cnpccurios）而不是 mixin 包内。
 * 教训 L3：mixin 包内的普通类被混入后的业务代码直接引用会 IllegalClassLoadError
 * （CNPCplus-Fix 实测过：bin.cnpcplus.mixin.ai.AiSpeedAccess 导致服务端崩、旧 NPC 读不出来）。
 * 本轮我自己也刚踩过同类问题（漏登记的 ContainerMenuAccessor 直接崩游戏），不再犯。
 */
public final class NpcSlotInheritance {

    /**
     * EntityType → 是否为 CNPC 的 NPC。
     * EntityType 实例在注册后不变，用并发 map 缓存判定结果，避免每次渲染/校验都反射一次。
     * getEntitySlots 在渲染与校验链上会被高频调用，这层缓存是必要的。
     */
    private static final Map<EntityType<?>, Boolean> NPC_TYPE_CACHE = new ConcurrentHashMap<>();

    private NpcSlotInheritance() {
    }

    /**
     * 判断给定实体类型是否是 CNPC 的 NPC。
     *
     * 判定方式：用 EntityType 的注册名判断命名空间是否为 customnpcs，
     * 并排除两个非 NPC 实体（椅子挂载点与投掷物，CNPC 也注册在同一命名空间下，
     * 见 CNPC1.21.1反编译 CustomEntities.registerNewentity 注册的
     * customnpcchairmount / customnpcprojectile）。
     *
     * 为什么不用 EntityType.create 造实例做 instanceof：
     * 那会在校验链里凭空创建实体，代价高且有副作用。注册名判断零副作用。
     */
    public static boolean isNpcType(EntityType<?> type) {
        if (type == null) return false;

        Boolean cached = NPC_TYPE_CACHE.get(type);
        if (cached != null) return cached;

        boolean result = false;
        try {
            String id = EntityType.getKey(type).toString();
            // CNPC 全部 NPC 都在 customnpcs 命名空间下
            if (id.startsWith("customnpcs:") || id.startsWith("customnpcs.")) {
                // 排除非 NPC 实体：椅子挂载点、投掷物
                result = !id.contains("chairmount") && !id.contains("projectile");
            }
        } catch (Throwable ignored) {
            // 注册表未就绪等异常情况一律视为「不是 NPC」，安全降级
            result = false;
        }

        NPC_TYPE_CACHE.put(type, result);
        return result;
    }

    /**
     * 把玩家拥有的槽位并入 NPC 的槽位表。
     *
     * @param manager  必须与调用方同一实例（SERVER 或 CLIENT）。
     *                 两个实例各自持有独立的 entitySlots 字段，混用会让服务端读到客户端的空表。
     * @param original NPC 原本的槽位表（来自我方数据包 cnpc_entities.json）
     * @return 合并后的新表；若无需改动则返回 null（由调用方保持原返回值）
     */
    public static Map<String, ISlotType> merge(CuriosEntityManager manager,
                                               Map<String, ISlotType> original) {
        if (manager == null) return null;

        // 玩家的槽位表：各饰品 mod 基本都会给 player 发槽（terra_curio / twilightforest / artifacts 实测如此）
        Map<String, ISlotType> playerSlots = manager.getEntitySlots(EntityType.PLAYER);
        if (playerSlots == null || playerSlots.isEmpty()) return null;

        Map<String, ISlotType> result = new HashMap<>();
        // 先放玩家槽位，再用 NPC 自己的数据包定义覆盖 ——
        // 这样我方 cnpccurios 数据包里对同名槽位的自定义（如 curio 槽 size=6）优先生效
        result.putAll(playerSlots);
        if (original != null) {
            result.putAll(original);
        }

        // 与原表完全一致就不必替换返回值，减少无谓的对象创建
        if (original != null && result.size() == original.size()
                && original.keySet().containsAll(result.keySet())) {
            return null;
        }
        return result;
    }

    /** 供调试/排查用：清空类型判定缓存（例如注册表重载后） */
    public static void clearCache() {
        NPC_TYPE_CACHE.clear();
    }

    /**
     * 备用判定：直接用实体实例判断，供确定持有实例的场景使用。
     * 保留是因为 instanceof 判定比注册名更精确（能覆盖第三方继承 EntityNPCInterface 的情况）。
     */
    public static boolean isNpc(Object entity) {
        return entity instanceof EntityNPCInterface;
    }

    /**
     * 判断某个物品能否放进 NPC 的指定饰品槽。
     *
     * 在 Curios 自身校验之外，额外加一条「同一物品不得同时佩戴多份」的限制。
     *
     * ── 为什么必须加（哈基彬实测崩服，crash-2026-08-24_00.43.31-server.txt）──
     * 泰拉饰品 MayFlyAbilityValue 的合并函数（反编译 lambda$static$0）写的是：
     *     ImmutableMap.builder().putAll(mapA).putAll(mapB).build()
     * Guava 的 ImmutableMap.Builder 碰到重复 key 会直接抛
     * IllegalArgumentException: Multiple entries with same key。
     * 而 AccessoriesAttachment.flushAbility 会遍历该实体**全部饰品槽的全部格子**累加能力，
     * 于是同一件带 may_fly 的饰品（如 terra_curio:fairy_boots）占了两个槽时，
     * 泰拉就拿两份完全相同的 {fairy_boots: FlyStack} 去合并 → 服务端 tick 崩溃。
     * 崩溃报告里两个条目内容逐字相同，正是这个成因。
     *
     * 本 mod 让 NPC 继承了玩家槽位（accessory）后，同一件饰品可同时进 accessory 与
     * curio 万能槽（isStackValid 对 "curio" 是无条件放行的），因此必须在此掐断。
     * 这同时也符合泰拉原作「同款饰品不叠加」的设定。
     *
     * 注意：这是**校验层**限制，不修改泰拉任何代码，也不影响玩家自己的饰品栏。
     *
     * @param npc       目标 NPC
     * @param slotId    目标槽位类型 id
     * @param slotIndex 目标槽位在该类型内的索引
     * @param stack     待放入的物品
     * @return true 表示允许放入
     */
    public static boolean mayPlaceInNpcSlot(net.minecraft.world.entity.LivingEntity npc,
                                            String slotId, int slotIndex,
                                            net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (npc == null || slotId == null) return false;

        // 先过 Curios 自己的校验（槽位类型是否接受该物品）
        if (!top.theillusivec4.curios.api.CuriosApi.isStackValid(
                new top.theillusivec4.curios.api.SlotContext(slotId, npc, slotIndex, false, true),
                stack)) {
            return false;
        }

        // 再查是否已在别处戴着同一件物品
        return !isAlreadyEquipped(npc, slotId, slotIndex, stack);
    }

    /**
     * 该 NPC 是否已在「除目标槽位之外」的位置戴着同种物品。
     * 只比对物品种类（Item），不比对数量与组件 —— 泰拉的能力 key 就是 Item 的 ResourceKey，
     * 同种物品无论 NBT 是否一致都会产生相同 key，因此按 Item 比对才对得上崩溃成因。
     *
     * 供 CuriosEventHandler.onCanEquip 与槽位 mayPlace 共用，保证两条路径判定一致。
     */
    public static boolean isDuplicateEquip(net.minecraft.world.entity.LivingEntity npc,
                                           String targetSlotId, int targetIndex,
                                           net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (npc == null || targetSlotId == null) return false;
        return isAlreadyEquipped(npc, targetSlotId, targetIndex, stack);
    }

    private static boolean isAlreadyEquipped(net.minecraft.world.entity.LivingEntity npc,
                                             String targetSlotId, int targetIndex,
                                             net.minecraft.world.item.ItemStack stack) {
        var opt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(npc);
        var handler = opt.orElse(null);
        if (handler == null) return false;

        var curios = handler.getCurios();
        if (curios == null) return false;

        for (var entry : curios.entrySet()) {
            String slotId = entry.getKey();
            var stacksHandler = entry.getValue();
            if (stacksHandler == null) continue;

            var stacks = stacksHandler.getStacks();
            if (stacks == null) continue;

            for (int i = 0; i < stacks.getSlots(); i++) {
                // 跳过目标槽位自身：替换同格里的同种物品必须允许
                if (i == targetIndex && slotId.equals(targetSlotId)) continue;

                var existing = stacks.getStackInSlot(i);
                if (existing == null || existing.isEmpty()) continue;
                if (existing.getItem() == stack.getItem()) return true;
            }
        }
        return false;
    }

    /**
     * 清除该 NPC 身上重复佩戴的饰品：同种物品只保留遇到的第一个，其余清空。
     *
     * 用于修复「重复饰品已写入存档」的情况 —— 详见 CuriosEventHandler.onEntityTick 注释。
     * 泰拉的 flushAbility 每 tick 都跑，存档里带重复项会导致一进世界就反复崩，
     * 所以必须真正把数据改干净，不能只拦新装备。
     *
     * 性能：先只读扫描，发现重复才写入。绝大多数 tick 会在扫描阶段就返回，
     * 不产生任何写操作与同步开销。
     */
    public static void removeDuplicateCurios(net.minecraft.world.entity.LivingEntity npc) {
        if (npc == null) return;

        var handler = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(npc).orElse(null);
        if (handler == null) return;

        var curios = handler.getCurios();
        if (curios == null || curios.isEmpty()) return;

        // 记录已见过的物品种类；遇到第二次出现的同种物品就清空该格
        java.util.Set<net.minecraft.world.item.Item> seen = null;
        boolean changed = false;

        for (var entry : curios.entrySet()) {
            var stacksHandler = entry.getValue();
            if (stacksHandler == null) continue;

            var stacks = stacksHandler.getStacks();
            if (stacks == null) continue;

            for (int i = 0; i < stacks.getSlots(); i++) {
                var stack = stacks.getStackInSlot(i);
                if (stack == null || stack.isEmpty()) continue;

                // 延迟创建集合：完全没有饰品的 NPC 不产生任何对象
                if (seen == null) {
                    seen = new java.util.HashSet<>();
                }
                if (!seen.add(stack.getItem())) {
                    // 该物品种类此前已出现过 → 当前这格是重复，清空
                    stacks.setStackInSlot(i, net.minecraft.world.item.ItemStack.EMPTY);
                    changed = true;
                }
            }
        }

        if (changed && npc instanceof EntityNPCInterface cnpc) {
            // 数据被改动，通知客户端刷新（走 CNPC 自己的同步链）
            cnpc.updateClient = true;
        }
    }

    /** 当前缓存里被判定为 NPC 的类型数量，排查用 */
    public static int cachedNpcTypeCount() {
        int n = 0;
        for (Map.Entry<EntityType<?>, Boolean> e : NPC_TYPE_CACHE.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue())) n++;
        }
        return n;
    }
}
