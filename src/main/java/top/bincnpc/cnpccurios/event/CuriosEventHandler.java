package top.bincnpc.cnpccurios.event;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import noppes.npcs.entity.EntityNPCInterface;
import top.bincnpc.cnpccurios.NpcSlotInheritance;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * 处理CNPC实体的Curios饰品栏事件。
 *
 * 1.21.1 差异说明：
 * 1.20.1 版本里还有 onAttachCapabilities / onEntityJoinWorld 两个方法，但它们的方法体是空的
 * （只有 instanceof 判断后直接 return，不做任何事）。1.21.1 的 AttachCapabilitiesEvent 已被移除，
 * 且 Curios 9.5.1 自己会对全部 EntityType 注册 capability（Curios.java:122-148）。
 */
public class CuriosEventHandler {

    /**
     * NPC 死亡时饰品不掉落。
     * NPC 的库存由 CNPC 自己管理（掉落率在 NPC 物品栏界面配置），
     * 若让 Curios 也按自己的规则掉一份，会造成饰品重复掉落。
     */
    @SubscribeEvent
    public void onDropRules(DropRulesEvent event) {
        if (event.getEntity() instanceof EntityNPCInterface) {
            event.addOverride(stack -> true, ICurio.DropRule.ALWAYS_KEEP);
        }
    }

    /**
     * 禁止 NPC 把同一件饰品同时戴在多个槽位。
     *
     * ── 为什么放在这个事件上（而不是只靠槽位的 mayPlace）──
     * mayPlace 只拦「玩家在 GUI 里点击放入」这一条路径。而 Curios 的
     * DynamicStackHandler.isItemValid（反编译 :65-74）在校验末尾会发这个事件：
     *     boolean canEquip = (isStackValid && canEquip && super.isItemValid);
     *     CurioCanEquipEvent event = new CurioCanEquipEvent(...);
     *     NeoForge.EVENT_BUS.post(event);
     *     return event.getEquipResult() != TriState.FALSE;
     * 所以在这里否决可以覆盖**所有**经过 handler 的插入路径
     * （GUI、脚本 API、漏斗/自动化等），比 mayPlace 更靠内层、更全面。
     *
     * ── 为什么必须禁止（实测崩服）──
     * crash-2026-08-24_00.43.31-server.txt：
     *   IllegalArgumentException: Multiple entries with same key:
     *   ResourceKey[minecraft:item / terra_curio:fairy_boots]=FlyStack[...] and （内容逐字相同）
     * 泰拉饰品 MayFlyAbilityValue 的合并函数是
     * ImmutableMap.builder().putAll(a).putAll(b).build()，Guava 遇重复 key 直接抛异常；
     * 而 AccessoriesAttachment.flushAbility 会遍历该实体全部饰品槽全部格子累加能力。
     * 同一件带 may_fly 的饰品占两个槽 → 两份相同 key → 服务端 tick 崩溃。
     * 这属于泰拉自身的容错缺陷，但触发条件由本 mod 的槽位继承带来，必须在此掐断。
     */
    @SubscribeEvent
    public void onCanEquip(CurioCanEquipEvent event) {
        // 只管 CNPC 的 NPC，绝不影响玩家与其他 mod 的生物
        if (!(event.getEntity() instanceof EntityNPCInterface npc)) return;
        // 已被其他逻辑否决的就不必再处理
        if (event.getEquipResult() == TriState.FALSE) return;

        SlotContext ctx = event.getSlotContext();
        if (ctx == null) return;

        if (NpcSlotInheritance.isDuplicateEquip(npc, ctx.identifier(), ctx.index(),
                event.getStack())) {
            event.setEquipResult(TriState.FALSE);
        }
    }

    /**
     * 清除 NPC 身上「已经存在」的重复饰品。
     *
     * ── 为什么仅靠上面的禁止装备不够 ──
     * 哈基彬崩服那一局，重复饰品**已经写进 NPC 数据里了**。
     * 而 Curios 的 CuriosEventHandler.tick(EntityTickEvent.Post)（反编译 :610-624）
     * 每 tick 都会遍历饰品栏并触发泰拉的 flushAbility，
     * 于是存档里的重复饰品会让世界一进去就反复崩溃 —— 只做「禁止新装」救不回已有存档。
     *
     * 因此这里在**同一个事件**上以更高优先级先跑一遍清理：
     * 同种物品只保留第一个，其余格子清空。这样在泰拉的处理器执行前，重复项已经不存在。
     *
     * 优先级说明：NeoForge 事件按优先级从高到低执行，HIGHEST 先于 Curios/泰拉的默认 NORMAL，
     * 保证「先清理、后累加能力」的顺序。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)) return;
        // 只在服务端清理，避免与客户端同步数据打架（客户端会收服务端的同步结果）
        if (npc.level() == null || npc.level().isClientSide()) return;

        NpcSlotInheritance.removeDuplicateCurios(npc);
    }
}
