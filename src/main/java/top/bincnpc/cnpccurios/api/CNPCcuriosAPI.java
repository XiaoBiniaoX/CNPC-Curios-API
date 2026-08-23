package top.bincnpc.cnpccurios.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CNPC Curios 脚本 API。
 * 供 CustomNPCs 脚本通过 Java.type() 调用。
 *
 * 方法签名与 1.20.1 版本完全一致，已有脚本无需修改。
 *
 * 使用示例 (JavaScript):
 * <pre>
 *   var CnpcCurios = Java.type("top.bincnpc.cnpccurios.api.CNPCcuriosAPI");
 *   var npc = event.npc.getMCEntity();
 *
 *   // 获取所有饰品槽ID
 *   var slotIds = CnpcCurios.getCuriosSlotIds(npc);
 *
 *   // 获取 ring 槽第0格的物品
 *   var stack = CnpcCurios.getCuriosStack(npc, "ring", 0);
 *
 *   // 设置 necklace 槽第0格的物品
 *   CnpcCurios.setCuriosStack(npc, "necklace", 0, someItemStack);
 * </pre>
 */
public class CNPCcuriosAPI {

    /**
     * 获取NPC的所有饰品槽类型ID列表
     * @param entity NPC实体 (LivingEntity / EntityNPCInterface)
     * @return 槽位ID列表，如 ["ring", "necklace", "head", ...]
     */
    public static List<String> getCuriosSlotIds(LivingEntity entity) {
        List<String> result = new ArrayList<>();
        if (!(entity instanceof EntityNPCInterface)) return result;

        CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                result.addAll(handler.getCurios().keySet()));
        return result;
    }

    /**
     * 获取NPC指定饰品槽中的物品
     * @param entity NPC实体
     * @param slotId 饰品槽ID，如 "ring", "necklace"
     * @param index  槽内索引（0-based）
     * @return 物品ItemStack（可能为空），如果槽位不存在则返回 ItemStack.EMPTY
     */
    public static ItemStack getCuriosStack(LivingEntity entity, String slotId, int index) {
        IDynamicStackHandler stackHandler = stacks(entity, slotId);
        if (stackHandler == null) return ItemStack.EMPTY;
        if (index < 0 || index >= stackHandler.getSlots()) return ItemStack.EMPTY;

        return stackHandler.getStackInSlot(index);
    }

    /**
     * 设置NPC指定饰品槽中的物品
     * @param entity NPC实体
     * @param slotId 饰品槽ID
     * @param index  槽内索引（0-based）
     * @param stack  要设置的物品（传 null 视为清空）
     */
    public static void setCuriosStack(LivingEntity entity, String slotId, int index, ItemStack stack) {
        if (!(entity instanceof EntityNPCInterface npc)) return;

        IDynamicStackHandler stackHandler = stacks(entity, slotId);
        if (stackHandler == null) return;
        if (index < 0 || index >= stackHandler.getSlots()) return;

        // 允许脚本传 null，统一转为 EMPTY，避免 Curios 内部 @Nonnull 约束被破坏
        stackHandler.setStackInSlot(index, stack != null ? stack : ItemStack.EMPTY);
        // 标记需要下发同步，走 CNPC 自己的同步链（EntityNPCInterface.tick → updateClient()）
        npc.updateClient = true;
    }

    /**
     * 获取NPC所有饰品槽的物品（用于遍历）
     * @param entity NPC实体
     * @return Map&lt;槽位ID, List&lt;物品&gt;&gt;  每个槽位ID对应其所有槽格的物品列表
     */
    public static Map<String, List<ItemStack>> getAllCurios(LivingEntity entity) {
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();
        if (!(entity instanceof EntityNPCInterface)) return result;

        CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                handler.getCurios().forEach((slotId, stacksHandler) -> {
                    IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                    List<ItemStack> items = new ArrayList<>();
                    for (int i = 0; i < stackHandler.getSlots(); i++) {
                        items.add(stackHandler.getStackInSlot(i));
                    }
                    result.put(slotId, items);
                }));
        return result;
    }

    /**
     * 获取NPC某个饰品槽的槽位数量
     * @param entity NPC实体
     * @param slotId 饰品槽ID
     * @return 槽位数量，如果槽位不存在返回0
     */
    public static int getCuriosSlotCount(LivingEntity entity, String slotId) {
        IDynamicStackHandler stackHandler = stacks(entity, slotId);
        return stackHandler == null ? 0 : stackHandler.getSlots();
    }

    /**
     * 获取NPC所有饰品槽的总槽位数量
     */
    public static int getTotalCuriosSlots(LivingEntity entity) {
        if (!(entity instanceof EntityNPCInterface)) return 0;

        int[] total = {0};
        CuriosApi.getCuriosInventory(entity).ifPresent(handler ->
                handler.getCurios().forEach((slotId, stacksHandler) ->
                        total[0] += stacksHandler.getStacks().getSlots()));
        return total[0];
    }

    /**
     * 内部工具：取出指定槽位类型的 stack handler，任何一环拿不到都返回 null。
     * 脚本传进来的实体和槽位名都不可信，这里逐层做非空判断（约法第7条第③项）。
     */
    private static IDynamicStackHandler stacks(LivingEntity entity, String slotId) {
        if (!(entity instanceof EntityNPCInterface)) return null;
        if (slotId == null || slotId.isEmpty()) return null;

        Optional<ICuriosItemHandler> opt = CuriosApi.getCuriosInventory(entity);
        ICuriosItemHandler handler = opt.orElse(null);
        if (handler == null) return null;

        var stacksHandler = handler.getCurios().get(slotId);
        if (stacksHandler == null) return null;

        return stacksHandler.getStacks();
    }
}
