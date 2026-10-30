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

/** 隔离第三方饰品渲染器，避免单件饰品破坏后续饰品或 NPC 的渲染状态。 */
public final class RenderIsolation {
    private static final Map<LivingEntity, Long> LAST_ERROR = new WeakHashMap<>();

    private RenderIsolation() {}

    /** 只复制当前矩阵，子渲染器即使 push/pop 失衡也接触不到调用方矩阵栈。 */
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
            render.accept(copyPose(pose));
        } catch (RuntimeException | LinkageError error) {
            // 同一 NPC 最多每分钟记录一次，避免坏饰品每帧刷爆日志。
            long now = System.nanoTime();
            Long previous = LAST_ERROR.get(npc);
            if (previous == null || now - previous > 60_000_000_000L) {
                LAST_ERROR.put(npc, now);
                CNPCcurios.LOGGER.error("已跳过 NPC {} 的异常饰品渲染器 {}；其他饰品继续渲染",
                        npc.getUUID(), source, error);
            }
        } finally {
            state.restore();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    /** 保存第三方渲染器最常改动的人形模型状态，单件饰品结束后完整恢复。 */
    public static final class ModelState {
        private record PartState(PartPose pose, float sx, float sy, float sz,
                                 boolean visible, boolean skipDraw) {
            PartState(ModelPart part) {
                this(part.storePose(), part.xScale, part.yScale, part.zScale,
                        part.visible, part.skipDraw);
            }

            void restore(ModelPart part) {
                part.loadPose(pose);
                part.xScale = sx;
                part.yScale = sy;
                part.zScale = sz;
                part.visible = visible;
                part.skipDraw = skipDraw;
            }
        }

        private final EntityModel<?> model;
        private final boolean young;
        private final boolean riding;
        private final boolean crouching;
        private final float attackTime;
        private final HumanoidModel.ArmPose leftArmPose;
        private final HumanoidModel.ArmPose rightArmPose;
        private final Map<ModelPart, PartState> parts = new IdentityHashMap<>();

        public ModelState(EntityModel<?> model) {
            this.model = model;
            this.young = model.young;
            this.riding = model.riding;
            this.attackTime = model.attackTime;
            if (model instanceof HumanoidModel<?> body) {
                this.crouching = body.crouching;
                this.leftArmPose = body.leftArmPose;
                this.rightArmPose = body.rightArmPose;
                for (ModelPart root : new ModelPart[]{body.head, body.hat, body.body, body.leftArm,
                        body.rightArm, body.leftLeg, body.rightLeg}) {
                    capture(root);
                }
                if (body instanceof PlayerModel<?> player) {
                    for (ModelPart root : new ModelPart[]{player.jacket, player.leftSleeve,
                            player.rightSleeve, player.leftPants, player.rightPants}) {
                        capture(root);
                    }
                }
            } else {
                this.crouching = false;
                this.leftArmPose = null;
                this.rightArmPose = null;
            }
        }

        private void capture(ModelPart root) {
            root.getAllParts().forEach(part -> parts.putIfAbsent(part, new PartState(part)));
        }

        public void restore() {
            model.young = young;
            model.riding = riding;
            model.attackTime = attackTime;
            if (model instanceof HumanoidModel<?> body) {
                body.crouching = crouching;
                body.leftArmPose = leftArmPose;
                body.rightArmPose = rightArmPose;
            }
            parts.forEach((part, state) -> state.restore(part));
        }
    }
}
