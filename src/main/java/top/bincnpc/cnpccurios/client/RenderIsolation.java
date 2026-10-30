package top.bincnpc.cnpccurios.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.LivingEntity;
import top.bincnpc.cnpccurios.CNPCcurios;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/** Keep third-party renderers from changing the next accessory/NPC's state. */
public final class RenderIsolation {
    private static final Map<LivingEntity, Long> LAST_ERROR = new WeakHashMap<>();

    private RenderIsolation() {}

    public static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    public static void run(LivingEntity npc, String source, EntityModel<?> model, PoseStack pose,
                           Consumer<PoseStack> render) {
        ModelState state = new ModelState(model);
        float[] color = RenderSystem.getShaderColor().clone();
        try {
            // An unbalanced push/pop, including over-pop, cannot damage the caller's stack.
            render.accept(copyPose(pose));
        } catch (RuntimeException | LinkageError error) {
            long now = System.nanoTime();
            Long previous = LAST_ERROR.get(npc);
            if (previous == null || now - previous > 60_000_000_000L) {
                LAST_ERROR.put(npc, now);
                CNPCcurios.LOGGER.error("Skipped NPC {} accessory renderer {}; other accessories remain active",
                        npc.getUUID(), source, error);
            }
        } finally {
            state.restore();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    public static final class ModelState {
        private record PartState(PartPose pose, float sx, float sy, float sz, boolean visible, boolean skip) {
            PartState(ModelPart part) {
                this(part.storePose(), part.xScale, part.yScale, part.zScale, part.visible, part.skipDraw);
            }
            void restore(ModelPart part) {
                part.loadPose(pose);
                part.xScale = sx; part.yScale = sy; part.zScale = sz;
                part.visible = visible; part.skipDraw = skip;
            }
        }

        private final EntityModel<?> model;
        private final boolean young, riding, crouching;
        private final float attackTime;
        private final HumanoidModel.ArmPose leftArmPose, rightArmPose;
        private final Map<ModelPart, PartState> parts = new IdentityHashMap<>();

        public ModelState(EntityModel<?> model) {
            this.model = model;
            young = model.young; riding = model.riding; attackTime = model.attackTime;
            if (model instanceof HumanoidModel<?> body) {
                crouching = body.crouching;
                leftArmPose = body.leftArmPose; rightArmPose = body.rightArmPose;
                for (ModelPart root : new ModelPart[]{body.head, body.hat, body.body, body.leftArm,
                        body.rightArm, body.leftLeg, body.rightLeg}) capture(root);
                if (body instanceof PlayerModel<?> player) {
                    for (ModelPart root : new ModelPart[]{player.jacket, player.leftSleeve, player.rightSleeve,
                            player.leftPants, player.rightPants}) capture(root);
                }
            } else {
                crouching = false;
                leftArmPose = rightArmPose = null;
            }
        }

        private void capture(ModelPart root) {
            root.getAllParts().forEach(part -> parts.putIfAbsent(part, new PartState(part)));
        }

        public void restore() {
            model.young = young; model.riding = riding; model.attackTime = attackTime;
            if (model instanceof HumanoidModel<?> body) {
                body.crouching = crouching;
                body.leftArmPose = leftArmPose; body.rightArmPose = rightArmPose;
            }
            parts.forEach((part, state) -> state.restore(part));
        }
    }
}
