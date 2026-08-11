package top.bincnpc.cnpccurios.mixin.mm;

import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.SlotContext;

/**
 * 铁魔法 (Iron's Spells 'n Spellbooks) 兼容补丁。
 *
 * 铁魔法的CurioBaseItem及其子类（SpellBook、各类戒指项链等）默认canEquip返回true，
 * 但部分饰品可能在特定条件下拒绝非玩家实体。
 * 本补丁确保所有CurioBaseItem对NPC总是可装备。
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.item.curios.CurioBaseItem", remap = false)
public abstract class MixinCurioBaseItem {

    /**
     * 修补 canEquip：对NPC确保返回true。
     */
    @Inject(method = "canEquip", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCanEquip(SlotContext slotContext, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slotContext.entity() instanceof EntityNPCInterface) {
            cir.setReturnValue(true);
        }
    }
}
