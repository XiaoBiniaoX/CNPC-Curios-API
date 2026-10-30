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

/** Entity owns ForgeCaps serialization, not NPC's additional-data methods. */
@Mixin(Entity.class)
public abstract class MixinEntityCuriosPersistence {
    @ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true)
    private CompoundTag cnpccurios$restore(CompoundTag tag) {
        return (Object) this instanceof EntityNPCInterface npc ? NpcCuriosStore.prepareLoad(npc, tag) : tag;
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void cnpccurios$externalize(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if ((Object) this instanceof EntityNPCInterface npc) NpcCuriosStore.save(npc, cir.getReturnValue());
    }
}
