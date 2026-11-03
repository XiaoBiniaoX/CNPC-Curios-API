package top.bincnpc.cnpccurios.mixin;

import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 饰品栏读取 NBT 时会把 canEquip 校验失败的饰品交给 handleInvalidStacks，
 * 非玩家实体会被直接 spawnAtLocation（表现为“复制 NPC 后饰品掉出来、槽位清空”）。
 * NPC 的槽位由本模组管理：读取期间先暂存，槽位重建完成后放回可接受的空槽；
 * 所有槽位都放不下时才保留原版掉落，不凭空删除物品。
 */
@Pseudo
@Mixin(value = CurioInventoryCapability.CurioInventoryWrapper.class, remap = false)
public abstract class MixinNpcInvalidCurios {

    @Unique private static final Map<Object, List<ItemStack>> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Shadow public abstract LivingEntity getWearer();

    @Shadow public abstract Map<String, ICurioStacksHandler> getCurios();

    @Inject(method = "readTag", at = @At("HEAD"), remap = false)
    private void cnpccurios$beginRead(Tag nbt, CallbackInfo ci) {
        if (getWearer() instanceof EntityNPCInterface) PENDING.remove(this);
    }

    @Inject(method = "loseInvalidStack", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpccurios$stashInvalid(ItemStack stack, CallbackInfo ci) {
        if (stack.isEmpty() || !(getWearer() instanceof EntityNPCInterface)) return;
        PENDING.computeIfAbsent(this, key -> new ArrayList<>()).add(stack);
        ci.cancel();
    }

    @Inject(method = "readTag", at = @At("RETURN"), remap = false)
    private void cnpccurios$restoreInvalid(Tag nbt, CallbackInfo ci) {
        List<ItemStack> pending = PENDING.remove(this);
        if (pending == null || pending.isEmpty() || !(getWearer() instanceof EntityNPCInterface)) return;

        for (ItemStack stack : pending) {
            if (!cnpccurios$place(stack)) getWearer().spawnAtLocation(stack, 1.0F);
        }
    }

    @Unique
    private boolean cnpccurios$place(ItemStack stack) {
        for (ICurioStacksHandler handler : getCurios().values()) {
            var stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (stacks.getStackInSlot(i).isEmpty() && stacks.isItemValid(i, stack)) {
                    stacks.setStackInSlot(i, stack);
                    return true;
                }
            }
        }
        return false;
    }
}
