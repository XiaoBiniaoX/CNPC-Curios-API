package top.bincnpc.cnpccurios.api;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.*;

/**
 * CNPC Curios 脚本 API。
 * 供 CustomNPCs 脚本通过 Java.type() 调用。
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

        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            result.addAll(handler.getCurios().keySet());
        });
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
        if (!(entity instanceof EntityNPCInterface)) return ItemStack.EMPTY;

        var opt = CuriosApi.getCuriosInventory(entity);
        if (!opt.isPresent()) return ItemStack.EMPTY;

        ICuriosItemHandler handler = opt.orElse(null);
        if (handler == null) return ItemStack.EMPTY;

        var curiosMap = handler.getCurios();
        var stacksHandler = curiosMap.get(slotId);
        if (stacksHandler == null) return ItemStack.EMPTY;

        IDynamicStackHandler stackHandler = stacksHandler.getStacks();
        if (index < 0 || index >= stackHandler.getSlots()) return ItemStack.EMPTY;

        return stackHandler.getStackInSlot(index);
    }

    /**
     * 设置NPC指定饰品槽中的物品
     * @param entity NPC实体
     * @param slotId 饰品槽ID
     * @param index  槽内索引（0-based）
     * @param stack  要设置的物品
     */
    public static void setCuriosStack(LivingEntity entity, String slotId, int index, ItemStack stack) {
        if (!(entity instanceof EntityNPCInterface npc)) return;
        final ItemStack s = stack != null ? stack : ItemStack.EMPTY;

        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            var curiosMap = handler.getCurios();
            var stacksHandler = curiosMap.get(slotId);
            if (stacksHandler == null) return;

            IDynamicStackHandler stackHandler = stacksHandler.getStacks();
            if (index < 0 || index >= stackHandler.getSlots()) return;

            stackHandler.setStackInSlot(index, s);
            npc.updateClient = true;
        });
    }

    /**
     * 获取NPC所有饰品槽的物品（用于遍历）
     * @param entity NPC实体
     * @return Map<槽位ID, List<物品>>  每个槽位ID对应其所有槽格的物品列表
     */
    public static Map<String, List<ItemStack>> getAllCurios(LivingEntity entity) {
        Map<String, List<ItemStack>> result = new LinkedHashMap<>();
        if (!(entity instanceof EntityNPCInterface)) return result;

        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            handler.getCurios().forEach((slotId, stacksHandler) -> {
                IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < stackHandler.getSlots(); i++) {
                    items.add(stackHandler.getStackInSlot(i));
                }
                result.put(slotId, items);
            });
        });
        return result;
    }

    /**
     * 获取NPC某个饰品槽的槽位数量
     * @param entity NPC实体
     * @param slotId 饰品槽ID
     * @return 槽位数量，如果槽位不存在返回0
     */
    public static int getCuriosSlotCount(LivingEntity entity, String slotId) {
        if (!(entity instanceof EntityNPCInterface)) return 0;

        var opt = CuriosApi.getCuriosInventory(entity);
        if (!opt.isPresent()) return 0;

        ICuriosItemHandler handler = opt.orElse(null);
        if (handler == null) return 0;

        var curiosMap = handler.getCurios();
        var stacksHandler = curiosMap.get(slotId);
        if (stacksHandler == null) return 0;

        return stacksHandler.getStacks().getSlots();
    }

    /**
     * 获取NPC所有饰品槽的总槽位数量
     */
    public static int getTotalCuriosSlots(LivingEntity entity) {
        if (!(entity instanceof EntityNPCInterface)) return 0;

        int[] total = {0};
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            handler.getCurios().forEach((slotId, stacksHandler) -> {
                total[0] += stacksHandler.getStacks().getSlots();
            });
        });
        return total[0];
    }
}
