package top.bincnpc.cnpccurios.persist;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把「放错槽位的饰品」在 NBT 层归位到真正兼容的空槽。
 *
 * ── 为什么需要这一层（哈基彬实测：复制魔杖生成的 NPC 饰品掉出、槽位清空）──
 * 旧版本的快捷放入只查 {@code CuriosApi.isStackValid}。Curios 的实现
 * （CuriosImplMixinHooks.java:135-148）在物品能进通用 "curio" 槽时，对**任意**
 * 槽位 id 都返回 true：
 *     return id.equals("curio") || slots.contains(id) || slots.contains("curio");
 * 于是 Fieldframe 的腰带会被放进排序更靠前的 mask/ring 等槽。
 *
 * 这些物品自己的 canEquip 是严格的：
 *     ModularBeltItem.java:27-29 → 只接受 "belt"
 *     WristWatchItem.java:41-43  → 只接受 "wrist"
 * 反序列化时 CurioInventory.init（CurioInventory.java:137-141）用
 * DynamicStackHandler.isItemValid（DynamicStackHandler.java:65-73）复检 canEquip，
 * 不通过就 loseInvalidStack；下一 tick 由
 * CurioInventoryCapability.handleInvalidStacks 以掉落物抛出。
 * 克隆生成、重进世界都会走这条反序列化链，所以旧存档里已经错放的物品
 * 光靠「修好放置校验」并不会自愈，必须在这里搬运一次。
 *
 * 本类只做两件事：判断每件饰品在**当前槽位**是否真的会被 Curios 保留；
 * 不会保留就搬到第一个「物品自己认可、且当前为空」的槽位。
 * 判定口径与 CurioInventory.init 一致（isStackValid + ICurio.canEquip + 槽位容量）。
 *
 * 找不到兼容空槽时**原样保留**：交给 Curios 原来的逻辑处理，绝不静默删物品。
 */
final class CuriosSlotRelocation {

    private static final String CURIOS = "Curios";
    private static final String IDENTIFIER = "Identifier";
    private static final String HANDLER = "StacksHandler";
    private static final String STACKS = "Stacks";
    private static final String COSMETICS = "Cosmetics";
    private static final String ITEMS = "Items";
    private static final String SIZE = "Size";
    private static final String SLOT = "Slot";
    private static final String ITEM_ID = "id";
    private static final String[] LISTS = {STACKS, COSMETICS};

    private CuriosSlotRelocation() {
    }

    /**
     * 原地归位 {@code data}（即 {@code curios:inventory} 的 CompoundTag）。
     *
     * @param data   库存 NBT；没有 Curios 列表时直接返回 false
     * @param wearer 穿戴者；为 null（无游戏上下文的自检/纯文件操作）时不做任何改动
     * @return true 表示确实发生了搬运，调用方应把结果重新落盘
     */
    static boolean relocateInPlace(CompoundTag data, LivingEntity wearer) {
        if (data == null || wearer == null) return false;
        if (!data.contains(CURIOS, Tag.TAG_LIST)) return false;

        ListTag curios = data.getList(CURIOS, Tag.TAG_COMPOUND);
        if (curios.isEmpty()) return false;

        Map<String, ISlotType> entitySlots = CuriosApi.getEntitySlots(wearer);
        if (entitySlots == null || entitySlots.isEmpty()) return false;

        // identifier -> StacksHandler，保持存档里第一次出现的条目
        Map<String, CompoundTag> handlers = new HashMap<>();
        for (int i = 0; i < curios.size(); i++) {
            CompoundTag entry = curios.getCompound(i);
            String identifier = entry.getString(IDENTIFIER);
            if (!identifier.isEmpty() && entry.contains(HANDLER, Tag.TAG_COMPOUND)) {
                handlers.putIfAbsent(identifier, entry.getCompound(HANDLER));
            }
        }
        if (handlers.isEmpty()) return false;

        // 先摊平成条目表，再统一决定去留；避免边遍历边改列表。
        List<Entry> entries = new ArrayList<>();
        boolean misplaced = false;
        for (Map.Entry<String, CompoundTag> handlerEntry : handlers.entrySet()) {
            String identifier = handlerEntry.getKey();
            for (String listType : LISTS) {
                CompoundTag handler = handlerEntry.getValue();
                if (!handler.contains(listType, Tag.TAG_COMPOUND)) continue;
                ListTag items = handler.getCompound(listType).getList(ITEMS, Tag.TAG_COMPOUND);
                for (int i = 0; i < items.size(); i++) {
                    CompoundTag itemTag = items.getCompound(i);
                    ItemStack stack = parse(wearer, itemTag);
                    Entry entry = new Entry(identifier, listType, itemTag.getInt(SLOT), itemTag, stack);
                    entries.add(entry);
                    // 解析失败（物品被移除/数据异常）时不参与判定，原样保留
                    if (stack != null && !stack.isEmpty()
                            && !keeps(wearer, entitySlots, identifier, entry.index, stack)) {
                        misplaced = true;
                    }
                }
            }
        }
        // 全部合规：不重排、不改写，保持存档字节稳定（避免每次保存都触发写入）。
        if (!misplaced) return false;

        List<ISlotType> ordered = new ArrayList<>(entitySlots.values());
        ordered.sort(null); // ISlotType 自身按 order + identifier 排序

        // 已占用的 (identifier, listType, index)；只登记「确定不动」的条目。
        Map<String, Set<Integer>> occupied = new HashMap<>();
        for (Entry entry : entries) {
            boolean keep = entry.stack == null || entry.stack.isEmpty()
                    || keeps(wearer, entitySlots, entry.identifier, entry.index, entry.stack);
            if (keep) occupy(occupied, entry.identifier, entry.listType, entry.index);
        }

        boolean changed = false;
        for (Entry entry : entries) {
            if (entry.stack == null || entry.stack.isEmpty()) continue;
            if (keeps(wearer, entitySlots, entry.identifier, entry.index, entry.stack)) continue;

            boolean moved = false;
            for (ISlotType slotType : ordered) {
                String candidate = slotType.getIdentifier();
                int size = Math.max(0, slotType.getSize());
                for (int index = 0; index < size; index++) {
                    if (isOccupied(occupied, candidate, entry.listType, index)) continue;
                    if (!accepts(wearer, candidate, index, entry.stack)) continue;
                    entry.identifier = candidate;
                    entry.index = index;
                    occupy(occupied, candidate, entry.listType, index);
                    moved = true;
                    changed = true;
                    break;
                }
                if (moved) break;
            }
            if (!moved) {
                // 没有任何兼容空槽：留在原地，仍由 Curios 原逻辑处理。
                occupy(occupied, entry.identifier, entry.listType, entry.index);
            }
        }

        if (changed) rebuild(curios, handlers, entries, entitySlots);
        return changed;
    }

    /** 重建所有 Items 列表；只在实际发生搬运时调用。 */
    private static void rebuild(ListTag curios, Map<String, CompoundTag> handlers,
                                List<Entry> entries, Map<String, ISlotType> entitySlots) {
        for (CompoundTag handler : handlers.values()) {
            for (String listType : LISTS) {
                if (handler.contains(listType, Tag.TAG_COMPOUND)) {
                    handler.getCompound(listType).put(ITEMS, new ListTag());
                }
            }
        }

        Map<String, List<Entry>> groups = new HashMap<>();
        for (Entry entry : entries) {
            groups.computeIfAbsent(key(entry.identifier, entry.listType),
                    k -> new ArrayList<>()).add(entry);
        }

        for (List<Entry> group : groups.values()) {
            Entry sample = group.get(0);
            CompoundTag handler = handlers.get(sample.identifier);
            if (handler == null) {
                // 目标槽位在旧存档里没有条目：补一条，CurioInventory 会按实体槽位重建。
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString(IDENTIFIER, sample.identifier);
                handler = new CompoundTag();
                entryTag.put(HANDLER, handler);
                curios.add(entryTag);
                handlers.put(sample.identifier, handler);
            }

            CompoundTag list = handler.contains(sample.listType, Tag.TAG_COMPOUND)
                    ? handler.getCompound(sample.listType) : new CompoundTag();
            ISlotType slotType = entitySlots.get(sample.identifier);
            int required = 0;
            for (Entry entry : group) required = Math.max(required, entry.index + 1);
            int current = list.contains(SIZE, Tag.TAG_INT) ? list.getInt(SIZE) : 0;
            // 只放大不缩小：把 Size 改小会让超出的条目在 ItemStackHandler 反序列化时被静默丢弃，
            // 那是真正删物品。保持足够容量后，仍由 CurioInventory 走原来的 loseInvalidStack 抛出。
            list.putInt(SIZE, Math.max(current,
                    Math.max(required, slotType == null ? 0 : Math.max(0, slotType.getSize()))));
            ListTag items = new ListTag();
            for (Entry entry : group) {
                CompoundTag copy = entry.tag.copy();
                copy.putInt(SLOT, entry.index);
                items.add(copy);
            }
            list.put(ITEMS, items);
            handler.put(sample.listType, list);
        }
    }

    /**
     * 该物品放在该槽位后，Curios 反序列化时会不会保留它。
     * 三种会被丢掉的情形：槽位类型已不存在、索引超出槽位容量、物品自身 canEquip 不认。
     */
    private static boolean keeps(LivingEntity wearer, Map<String, ISlotType> entitySlots,
                                 String identifier, int index, ItemStack stack) {
        ISlotType slotType = entitySlots.get(identifier);
        if (slotType == null) return false;
        if (index < 0 || index >= Math.max(0, slotType.getSize())) return false;
        return accepts(wearer, identifier, index, stack);
    }

    /**
     * 与 Curios 反序列化时的判定完全一致。
     * 注意 CurioInventory.init:137-155 连 Cosmetic 也是用功能槽 handler 复检，
     * 所以这里对两份列表统一用 cosmetic=false，才能和「会不会被丢」的判断对齐。
     */
    private static boolean accepts(LivingEntity wearer, String identifier, int index, ItemStack stack) {
        SlotContext context = new SlotContext(identifier, wearer, index, false, true);
        if (!CuriosApi.isStackValid(context, stack)) return false;
        return CuriosApi.getCurio(stack).map(curio -> curio.canEquip(context)).orElse(true);
    }

    private static ItemStack parse(LivingEntity wearer, CompoundTag itemTag) {
        if (!itemTag.contains(ITEM_ID, Tag.TAG_STRING)) return null;
        try {
            return ItemStack.parse(wearer.registryAccess(), itemTag).orElse(ItemStack.EMPTY);
        } catch (RuntimeException malformed) {
            // 旧存档里被移除模组的物品等异常 NBT：不参与归位，保持原样。
            return null;
        }
    }

    private static String key(String identifier, String listType) {
        return identifier + '|' + listType;
    }

    private static void occupy(Map<String, Set<Integer>> occupied,
                               String identifier, String listType, int index) {
        occupied.computeIfAbsent(key(identifier, listType), k -> new HashSet<>()).add(index);
    }

    private static boolean isOccupied(Map<String, Set<Integer>> occupied,
                                      String identifier, String listType, int index) {
        Set<Integer> used = occupied.get(key(identifier, listType));
        return used != null && used.contains(index);
    }

    private static final class Entry {
        String identifier;
        final String listType;
        int index;
        final CompoundTag tag;
        final ItemStack stack;

        Entry(String identifier, String listType, int index, CompoundTag tag, ItemStack stack) {
            this.identifier = identifier;
            this.listType = listType;
            this.index = index;
            this.tag = tag;
            this.stack = stack;
        }
    }
}
