package top.bincnpc.cnpccurios.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import noppes.npcs.containers.ContainerNPCInv;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.bincnpc.cnpccurios.CNPCcurios;
import top.bincnpc.cnpccurios.CnpcCuriosConfig;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.lang.reflect.Method;

@Mixin(value = ContainerNPCInv.class, remap = false)
public abstract class MixinContainerNPCInv extends AbstractContainerMenu {

    @Unique private static Method sAddSlot;
    /** NPC原始slot数（盔甲4+物品3+背包9）+ 玩家背包(27+9) = 52 */
    @Unique private static final int NPC_BASE_SLOTS = 52;

    protected MixinContainerNPCInv() { super(null, 0); }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstructed(int containerId, Inventory playerInventory, int entityId, CallbackInfo ci) {
        try {
            EntityNPCInterface npc = getNpc(playerInventory, entityId);
            if (npc == null) return;
            injectSlots(npc);
        } catch (Throwable ignored) {}
    }

    @Unique
    private EntityNPCInterface getNpc(Inventory inv, int entityId) {
        try {
            var pf = Inventory.class.getField("f_35978_");
            Object player = pf.get(inv);
            var lm = player.getClass().getMethod("m_9236_");
            Object level = lm.invoke(player);
            var gm = level.getClass().getMethod("m_6815_", int.class);
            Entity e = (Entity) gm.invoke(level, entityId);
            if (e instanceof EntityNPCInterface n) return n;
        } catch (Throwable t) {
            try {
                var pf = Inventory.class.getField("player");
                Object player = pf.get(inv);
                var lm = player.getClass().getMethod("level");
                Object level = lm.invoke(player);
                var gm = level.getClass().getMethod("getEntity", int.class);
                Entity e = (Entity) gm.invoke(level, entityId);
                if (e instanceof EntityNPCInterface n) return n;
            } catch (Throwable t2) {}
        }
        return null;
    }

    @Unique
    private void injectSlots(EntityNPCInterface npc) {
        CuriosApi.getCuriosInventory(npc).ifPresent(handler -> {
            int idx = 0;
            for (var e : handler.getCurios().entrySet()) {
                String sid = e.getKey();
                var sh = e.getValue().getStacks();
                for (int i = 0; i < sh.getSlots(); i++) {
                    final int si = i; final String id = sid;
                    int col = idx / 6, row = idx % 6;
                    int x = CnpcCuriosConfig.SLOT_X.get() + col * 18;
                    int y = CnpcCuriosConfig.SLOT_Y.get() + row * 18;
                    addSlotReflect(new SlotItemHandler(sh, si, x, y) {
                        @Override public boolean mayPlace(ItemStack s) {
                            if (s.isEmpty()) return true;
                            return CuriosApi.isStackValid(new SlotContext(id, npc, si, false, true), s);
                        }
                        @Override public void setChanged() {
                            super.setChanged();
                            try { npc.updateClient = true; } catch (Exception ignored) {}
                        }
                    });
                    // 记录此slot index对应的curio槽位类型
                    CNPCcurios.SLOT_INDEX_TO_ID.put(NPC_BASE_SLOTS + idx, id);
                    idx++;
                }
            }
        });
    }

    @Unique
    private void addSlotReflect(Slot slot) {
        try {
            if (sAddSlot == null) {
                try { sAddSlot = AbstractContainerMenu.class.getDeclaredMethod("addSlot", Slot.class); }
                catch (NoSuchMethodException e) {
                    sAddSlot = AbstractContainerMenu.class.getDeclaredMethod("m_38897_", Slot.class);
                }
                sAddSlot.setAccessible(true);
            }
            sAddSlot.invoke(this, slot);
        } catch (Throwable ignored) {}
    }

}
