package top.bincnpc.cnpccurios.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpc.cnpccurios.persist.NpcCuriosStore;

/**
 * Curios 9.5.1 把库存放在 Entity 的 neoforge:attachments，而非 NPC additional data。
 * 因此必须挂 Entity 的完整 load/save，才能覆盖普通存档、克隆/导入与维度复制路径。
 */
@Mixin(value = Entity.class, remap = false)
public abstract class MixinEntityCuriosPersistence {
    @ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true, remap = false)
    private CompoundTag cnpccurios$restore(CompoundTag tag) {
        return (Object) this instanceof EntityNPCInterface npc
                ? NpcCuriosStore.prepareLoad(npc, tag) : tag;
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"), remap = false)
    private void cnpccurios$externalize(CompoundTag tag,
                                       CallbackInfoReturnable<CompoundTag> cir) {
        if ((Object) this instanceof EntityNPCInterface npc) {
            NpcCuriosStore.save(npc, cir.getReturnValue());
        }
    }
}
