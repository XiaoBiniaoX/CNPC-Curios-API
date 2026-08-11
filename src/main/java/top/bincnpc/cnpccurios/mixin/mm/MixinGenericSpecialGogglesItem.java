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
 * MM现代化mod兼容补丁：GenericSpecialGogglesItem（NVG夜视仪/TVG热成像/Visor面罩）
 *
 * MM原版限制：
 * - canEquip()：检查 !(entity instanceof Player)，非玩家直接返回false
 * - curioTick()：检查 !(entity instanceof Player)，非玩家直接跳过
 *   且对玩家还要求头盔带有特定tag，否则强制卸下饰品
 *
 * 本补丁：对 EntityNPCInterface 实体绕开上述限制，允许NPC直接穿戴并正常渲染。
 */
@Pseudo
@Mixin(targets = "net.tkg.ModernMayhem.server.item.generic.GenericSpecialGogglesItem", remap = false)
public abstract class MixinGenericSpecialGogglesItem {

    /**
     * 修补 canEquip：对NPC直接返回true，绕开头盔tag限制。
     */
    @Inject(method = "canEquip", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCanEquip(SlotContext slotContext, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slotContext.entity() instanceof EntityNPCInterface) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 修补 curioTick：对NPC直接跳过tick逻辑，防止MM强制卸下饰品。
     */
    @Inject(method = "curioTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCurioTick(SlotContext slotContext, ItemStack stack, CallbackInfo ci) {
        if (slotContext.entity() instanceof EntityNPCInterface) {
            ci.cancel();
        }
    }
}
