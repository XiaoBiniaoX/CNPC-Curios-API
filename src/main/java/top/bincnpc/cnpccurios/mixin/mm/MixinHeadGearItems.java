package top.bincnpc.cnpccurios.mixin.mm;

import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.SlotContext;

/**
 * MM现代化mod兼容补丁：HeadGearItems（头盔/头部饰品）
 *
 * MM原版限制：
 * - canEquip()：检查 !(entity instanceof Player)，非玩家直接返回false
 * - curioTick()：检查 !(entity instanceof Player)，非玩家直接跳过
 *   且type==5时还检查头盔是否与防毒面具不兼容，不兼容则强制卸下
 *
 * 本补丁：对 EntityNPCInterface 实体绕开上述限制。
 */
@Pseudo
@Mixin(targets = "net.tkg.ModernMayhem.server.item.curios.head.HeadGearItems", remap = false)
public abstract class MixinHeadGearItems {

    /**
     * 修补 canEquip：对NPC直接返回true，绕开头盔/防毒面具限制。
     */
    @Inject(method = "canEquip", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCanEquip(SlotContext slotContext, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slotContext.entity() instanceof EntityNPCInterface) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 修补 curioTick：对NPC直接跳过tick逻辑（尤其是防毒面具不兼容检查导致的强制卸下）。
     */
    @Inject(method = "curioTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCurioTick(SlotContext slotContext, ItemStack stack, CallbackInfo ci) {
        if (slotContext.entity() instanceof EntityNPCInterface) {
            ci.cancel();
        }
    }
}
