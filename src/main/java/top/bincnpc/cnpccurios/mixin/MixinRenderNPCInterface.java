package top.bincnpc.cnpccurios.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.mixin.LivingRenderer2Mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import top.bincnpc.cnpccurios.client.SafeNPCCuriosLayer;

import java.util.ArrayList;
import java.util.List;

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

    /** 本 mod 拥有的层；CNPC 重建共享 layers 后据此补回，不依赖一次性的构造注入。 */
    @Unique
    private final List<RenderLayer<T, M>> cnpccurios$ownedLayers = new ArrayList<>();

    /** 仅为满足编译（mixin 类不会被真正实例化） */
    private MixinRenderNPCInterface(EntityRendererProvider.Context ctx, M model, float shadow) {
        super(ctx, model, shadow);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/EntityModel;F)V",
            at = @At("RETURN"))
    private void cnpccurios$onConstructed(EntityRendererProvider.Context manager, M model, float shadowSize, CallbackInfo ci) {
        RenderLayer<T, M> layer = new SafeNPCCuriosLayer<>(this);
        cnpccurios$ownedLayers.add(layer);
        this.addLayer(layer);
    }

    /**
     * RenderCustomNpc 在模型切换时会 clear 当前共享 layers，再从自己的 npclayers 重建；
     * 构造后才加入的饰品层不在 npclayers 中，会永久丢失。父 render 的 HEAD 位于该重建之后，
     * 因此每次在这里把自有层放回最前方；先 remove 再按索引 add，保证不累积重复层。
     */
    @Inject(method = "render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void cnpccurios$restoreLayers(EntityNPCInterface npc, float yaw, float partialTick,
                                          PoseStack pose, MultiBufferSource buffer, int light,
                                          CallbackInfo ci) {
        List<RenderLayer<T, M>> layers = (List) ((LivingRenderer2Mixin) (Object) this).layers();
        for (int i = 0; i < cnpccurios$ownedLayers.size(); i++) {
            RenderLayer<T, M> layer = cnpccurios$ownedLayers.get(i);
            if (layers.indexOf(layer) != i) {
                layers.remove(layer);
                layers.add(i, layer);
            }
        }
    }
}
