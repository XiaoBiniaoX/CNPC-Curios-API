package top.bincnpc.cnpccurios.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/** Same isolation for optional WARBORN layers that do not use ICurioRenderer. */
public final class GuardedNPCLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final RenderLayer<T, M> delegate;

    public GuardedNPCLayer(RenderLayerParent<T, M> parent, RenderLayer<T, M> delegate) {
        super(parent);
        this.delegate = delegate;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, T npc, float swing,
                       float amount, float partialTick, float age, float yaw, float pitch) {
        RenderIsolation.run(npc, delegate.getClass().getName(), getParentModel(), pose,
                isolated -> delegate.render(isolated, buffer, light, npc, swing, amount, partialTick, age, yaw, pitch));
    }
}
