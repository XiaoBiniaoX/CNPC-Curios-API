package top.bincnpc.cnpccurios.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.client.render.CuriosLayer;

/**
 * 在 RenderNPCInterface 构造末尾注入 CuriosLayer，让 NPC 把佩戴的饰品渲染出来。
 *
 * 为什么必须自己注入（实证）：
 * Curios 9.5.1 只给「玩家」渲染器加 CuriosLayer —— Curios.java:234-249 的 addLayers
 * 仅遍历 evt.getSkins() 并对 evt.getSkin(model) 调 addLayer，没有覆盖其他实体渲染器。
 * 所以 NPC 不注入就完全不渲染饰品，且不存在重复注入导致画两遍的风险。
 *
 * 1.21.1 相比 1.20.1 的简化：
 * addLayer 在 1.21.1 是 public final（neoforge-21.1.235-sources.jar →
 * LivingEntityRenderer.java:43 public final boolean addLayer(RenderLayer<T,M>)），
 * 且运行时是 mojmap，所以 1.20.1 那套 m_115326_ 反射兜底全部删除。
 * 本 mixin 声明 extends LivingEntityRenderer 与目标类层级保持一致
 * （RenderNPCInterface extends LivingEntityRenderer，见 CNPC1.21.1反编译
 * RenderNPCInterface.java:65-66），从而能直接调用 addLayer。
 *
 * 目标构造签名取自 RenderNPCInterface.java:68
 * public RenderNPCInterface(EntityRendererProvider.Context manager, M model, float f)
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public abstract class MixinRenderNPCInterface<T extends net.minecraft.world.entity.LivingEntity, M extends EntityModel<T>>
        extends LivingEntityRenderer<T, M> {

    /** 仅为满足编译（mixin 类不会被真正实例化） */
    private MixinRenderNPCInterface(EntityRendererProvider.Context ctx, M model, float shadow) {
        super(ctx, model, shadow);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/EntityModel;F)V",
            at = @At("RETURN"))
    private void cnpccurios$onConstructed(EntityRendererProvider.Context manager, M model, float shadowSize, CallbackInfo ci) {
        // CuriosLayer 自己会在 render 时遍历实体的饰品栏，NPC 没有饰品栏时它什么都不画，
        // 因此这里无条件添加即可，不需要判断该 NPC 是否配了槽位。
        this.addLayer(new CuriosLayer<>(this));
    }
}
