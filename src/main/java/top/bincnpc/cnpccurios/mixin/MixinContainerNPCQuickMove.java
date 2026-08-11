package top.bincnpc.cnpccurios.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerNPCInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ContainerNPCInv.class, remap = false)
public abstract class MixinContainerNPCQuickMove extends AbstractContainerMenu {

    @Unique private static final int NPC_BASE_SLOTS = 52;

    protected MixinContainerNPCQuickMove() { super(null, 0); }

    @Inject(method = "m_7648_", at = @At("HEAD"), cancellable = true)
    private void onQuickMoveStack(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        try {
            int totalSlots = 0;
            for (int i = 0; i < 300; i++) {
                try { getSlot(i); totalSlots++; }
                catch (Exception e) { break; }
            }
            if (totalSlots <= NPC_BASE_SLOTS) return;

            // 点击饰品槽(>=52) → 移到玩家背包区(16-51)
            if (index >= NPC_BASE_SLOTS) {
                Slot slot = getSlot(index);
                if (slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    ItemStack result = stack.copy();
                    if (moveItemStackTo(stack, 16, 52, false)) {
                        slot.setChanged();
                        cir.setReturnValue(result);
                        return;
                    }
                }
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }

            // 点击玩家背包区(16-51)有饰品→自动穿戴到对应空饰品槽
            if (index >= 16 && index < 52) {
                Slot slot = getSlot(index);
                if (slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    for (int i = NPC_BASE_SLOTS; i < totalSlots; i++) {
                        Slot curioSlot = getSlot(i);
                        if (!curioSlot.hasItem() && curioSlot.mayPlace(stack)) {
                            ItemStack toMove = stack.copy();
                            if (moveItemStackTo(stack, i, i + 1, false)) {
                                slot.setChanged();
                                cir.setReturnValue(toMove);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[CNPCcurios] quickMoveStack: " + t);
        }
    }
}
