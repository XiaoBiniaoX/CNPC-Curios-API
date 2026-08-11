package top.bincnpc.cnpccurios.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import top.bincnpc.cnpccurios.CNPCcurios;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * ragdollified 可选兼容：在 NPC 死亡布娃娃上自绘护甲与 Curios 饰品。
 * 仅在 ragdollified 加载时生效；纯客户端；任何异常静默降级。
 *
 * 姿态数学照抄 ragdollified 的 renderHumanoidPartPhysics / renderBodyCurios，
 * 保证与布娃娃身体严格对齐。护甲/饰品数据实时读取死亡 NPC（尸体仍保留库存）。
 */
@Mod.EventBusSubscriber(modid = "cnpccurios", value = Dist.CLIENT)
public final class NpcRagdollRenderer {

    // RagdollPart.ordinal()：TORSO(0) HEAD(1) LEFT_LEG(2) RIGHT_LEG(3) LEFT_ARM(4) RIGHT_ARM(5)
    private static final int TORSO = 0, HEAD = 1, LEFT_LEG = 2, RIGHT_LEG = 3, LEFT_ARM = 4, RIGHT_ARM = 5;

    // ModelPart.setPos 用的部件枢轴（像素）
    private static final float[][] PIVOT_PX = {
            {0, -6, 0},     // TORSO
            {0, 4, 0},      // HEAD
            {0, -6, 0},     // LEFT_LEG
            {0, -6, 0},     // RIGHT_LEG
            {-1, -4, 0},    // LEFT_ARM
            {1, -4, 0},     // RIGHT_ARM
    };
    // poseStack.translate 用的部件枢轴（方块）
    private static final float[][] PIVOT_BL = {
            {0, -6f / 16f, 0},
            {0, 4f / 16f, 0},
            {0, -6f / 16f, 0},
            {0, -6f / 16f, 0},
            {-1f / 16f, -4f / 16f, 0},
            {1f / 16f, -4f / 16f, 0},
    };

    private static final Quaternionf QUAT = new Quaternionf();

    private static HumanoidModel<?> armorInner;
    private static HumanoidModel<?> armorOuter;

    private NpcRagdollRenderer() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;
        Collection<?> ragdolls = RagdollifiedBridge.allRagdolls();
        if (ragdolls.isEmpty()) return;
        double armorDistSq = RagdollifiedBridge.armorDistSq();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Object ragdoll : ragdolls) {
            if (RagdollifiedBridge.isDestroyed(ragdoll)) continue;
            Entity entity = level.getEntity(RagdollifiedBridge.originalEntityId(ragdoll));
            if (!(entity instanceof EntityNPCInterface npc)) continue;
            float[][] trs = new float[6][];
            for (int i = 0; i < 6; i++) {
                trs[i] = RagdollifiedBridge.smoothedTransform(ragdoll, i);
            }
            float[] torso = trs[TORSO];
            if (torso == null) continue;
            double dx = torso[0] - cam.x;
            double dy = torso[1] - cam.y;
            double dz = torso[2] - cam.z;
            if (dx * dx + dy * dy + dz * dz > armorDistSq) continue;
            int light = LevelRendererLight(level, torso);
            poseStack.pushPose();
            try {
                poseStack.translate(-cam.x, -cam.y, -cam.z);
                poseStack.translate(torso[0], torso[1] + RagdollifiedBridge.liquidBob(ragdoll), torso[2]);
                ensureModels();
                renderArmor(npc, poseStack, buffer, light, trs);
                renderCurios(npc, poseStack, buffer, light, partialTick, trs);
                drew = true;
            } catch (Exception e) {
                CNPCcurios.LOGGER.error("渲染NPC布娃娃护甲/饰品失败", e);
            } finally {
                poseStack.popPose();
            }
        }
        if (drew) {
            buffer.endBatch();
        }
    }

    private static int LevelRendererLight(Level level, float[] torso) {
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(level,
                BlockPos.containing(torso[0], torso[1] + 0.5, torso[2]));
    }

    // ---------------- 护甲 ----------------

    private static void renderArmor(EntityNPCInterface npc, PoseStack ps, MultiBufferSource buffer,
                                    int light, float[][] trs) {
        renderArmorSlot(npc, EquipmentSlot.HEAD, ps, buffer, light, trs);
        renderArmorSlot(npc, EquipmentSlot.CHEST, ps, buffer, light, trs);
        renderArmorSlot(npc, EquipmentSlot.LEGS, ps, buffer, light, trs);
        renderArmorSlot(npc, EquipmentSlot.FEET, ps, buffer, light, trs);
    }

    private static void renderArmorSlot(EntityNPCInterface npc, EquipmentSlot slot, PoseStack ps,
                                        MultiBufferSource buffer, int light, float[][] trs) {
        ItemStack stack = npc.getItemBySlot(slot);
        if (stack.isEmpty()) return;
        if (RagdollifiedBridge.isGeckoLibArmor(stack.getItem())) {
            renderGeckoSlot(stack, slot, ps, buffer, light, trs, npc);
            return;
        }
        if (!(stack.getItem() instanceof ArmorItem armor)) return;
        HumanoidModel<?> base = slot == EquipmentSlot.LEGS ? armorInner : armorOuter;
        HumanoidModel<?> model = resolveArmorModel(stack, slot, npc, base);
        boolean dyeable = armor instanceof DyeableLeatherItem;
        float[] tint = {1, 1, 1};
        if (dyeable) {
            int c = ((DyeableLeatherItem) armor).getColor(stack);
            tint[0] = (c >> 16 & 0xFF) / 255f;
            tint[1] = (c >> 8 & 0xFF) / 255f;
            tint[2] = (c & 0xFF) / 255f;
        }
        renderArmorParts(model, armorResource(stack, npc, slot, null), slot, ps, buffer, light, trs, tint);
        if (dyeable) {
            renderArmorParts(model, armorResource(stack, npc, slot, "overlay"), slot, ps, buffer, light, trs,
                    new float[]{1, 1, 1});
        }
    }

    private static void renderArmorParts(HumanoidModel<?> model, ResourceLocation texture, EquipmentSlot slot,
                                         PoseStack ps, MultiBufferSource buffer, int light, float[][] trs,
                                         float[] tint) {
        VertexConsumer vc = buffer.getBuffer(RenderType.armorCutoutNoCull(texture));
        switch (slot) {
            case HEAD -> renderPart(ps, vc, model.head, light, HEAD, trs, tint);
            case CHEST -> {
                renderPart(ps, vc, model.body, light, TORSO, trs, tint);
                renderPart(ps, vc, model.leftArm, light, LEFT_ARM, trs, tint);
                renderPart(ps, vc, model.rightArm, light, RIGHT_ARM, trs, tint);
            }
            case LEGS -> {
                renderPart(ps, vc, model.body, light, TORSO, trs, tint);
                renderPart(ps, vc, model.leftLeg, light, LEFT_LEG, trs, tint);
                renderPart(ps, vc, model.rightLeg, light, RIGHT_LEG, trs, tint);
            }
            case FEET -> {
                renderPart(ps, vc, model.leftLeg, light, LEFT_LEG, trs, tint);
                renderPart(ps, vc, model.rightLeg, light, RIGHT_LEG, trs, tint);
            }
            default -> {
            }
        }
    }

    private static void renderPart(PoseStack ps, VertexConsumer vc, ModelPart part, int light, int partIndex,
                                   float[][] trs, float[] tint) {
        float[] tr = trs[partIndex];
        float[] torso = trs[TORSO];
        if (tr == null || torso == null) return;
        ps.pushPose();
        try {
            ps.translate(tr[0] - torso[0], tr[1] - torso[1], tr[2] - torso[2]);
            QUAT.set(tr[3], tr[4], tr[5], tr[6]).rotateZ((float) Math.PI);
            ps.mulPose(QUAT);
            float[] pivot = PIVOT_PX[partIndex];
            part.setPos(pivot[0], pivot[1], pivot[2]);
            part.xRot = 0;
            part.yRot = 0;
            part.zRot = 0;
            part.render(ps, vc, light, OverlayTexture.NO_OVERLAY, tint[0], tint[1], tint[2], 1f);
        } finally {
            ps.popPose();
        }
    }

    private static void renderGeckoSlot(ItemStack stack, EquipmentSlot slot, PoseStack ps, MultiBufferSource buffer,
                                        int light, float[][] trs, LivingEntity entity) {
        HumanoidModel<?> base = armorInner;
        setAllVisible(base, false);
        base.young = false;
        base.riding = false;
        base.crouching = false;
        try {
            switch (slot) {
                case HEAD -> {
                    if (trs[HEAD] == null) return;
                    base.head.visible = true;
                    resetPart(base.head);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, HEAD, trs, entity);
                    base.head.visible = false;
                }
                case CHEST -> {
                    if (trs[TORSO] == null || trs[LEFT_ARM] == null || trs[RIGHT_ARM] == null) return;
                    base.body.visible = true;
                    resetPart(base.body);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, TORSO, trs, entity);
                    base.body.visible = false;
                    base.leftArm.visible = true;
                    resetPart(base.leftArm);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, LEFT_ARM, trs, entity);
                    base.leftArm.visible = false;
                    base.rightArm.visible = true;
                    resetPart(base.rightArm);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, RIGHT_ARM, trs, entity);
                    base.rightArm.visible = false;
                }
                case LEGS -> {
                    if (trs[TORSO] == null || trs[LEFT_LEG] == null || trs[RIGHT_LEG] == null) return;
                    base.body.visible = true;
                    resetPart(base.body);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, TORSO, trs, entity);
                    base.body.visible = false;
                    base.leftLeg.visible = true;
                    resetPart(base.leftLeg);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, LEFT_LEG, trs, entity);
                    base.leftLeg.visible = false;
                    base.rightLeg.visible = true;
                    resetPart(base.rightLeg);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, RIGHT_LEG, trs, entity);
                    base.rightLeg.visible = false;
                }
                case FEET -> {
                    if (trs[LEFT_LEG] == null || trs[RIGHT_LEG] == null) return;
                    base.leftLeg.visible = true;
                    resetPart(base.leftLeg);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, LEFT_LEG, trs, entity);
                    base.leftLeg.visible = false;
                    base.rightLeg.visible = true;
                    resetPart(base.rightLeg);
                    renderGeckoPart(stack, slot, ps, buffer, light, base, RIGHT_LEG, trs, entity);
                    base.rightLeg.visible = false;
                }
                default -> {
                }
            }
        } finally {
            setAllVisible(base, true);
        }
    }

    private static void renderGeckoPart(ItemStack stack, EquipmentSlot slot, PoseStack ps, MultiBufferSource buffer,
                                        int light, HumanoidModel<?> base, int partIndex, float[][] trs,
                                        LivingEntity entity) {
        float[] tr = trs[partIndex];
        float[] torso = trs[TORSO];
        if (tr == null || torso == null) return;
        ps.pushPose();
        try {
            ps.translate(tr[0] - torso[0], tr[1] - torso[1], tr[2] - torso[2]);
            QUAT.set(tr[3], tr[4], tr[5], tr[6]).rotateZ((float) Math.PI);
            ps.mulPose(QUAT);
            float[] pivot = PIVOT_BL[partIndex];
            ps.translate(pivot[0], pivot[1], pivot[2]);
            RagdollifiedBridge.renderGeckoLibArmor(stack, slot, entity, ps, buffer, light, base);
        } finally {
            ps.popPose();
        }
    }

    private static void resetPart(ModelPart part) {
        part.setPos(0, 0, 0);
        part.xRot = 0;
        part.yRot = 0;
        part.zRot = 0;
    }

    private static HumanoidModel<?> resolveArmorModel(ItemStack stack, EquipmentSlot slot, LivingEntity entity,
                                                      HumanoidModel<?> base) {
        try {
            HumanoidModel<?> custom = IClientItemExtensions.of(stack).getHumanoidArmorModel(entity, stack, slot, base);
            if (custom == null || custom == base) return base;
            setAllPartsVisible(custom);
            return custom;
        } catch (Exception e) {
            return base;
        }
    }

    private static void setAllVisible(HumanoidModel<?> model, boolean visible) {
        model.head.visible = visible;
        model.body.visible = visible;
        model.rightArm.visible = visible;
        model.leftArm.visible = visible;
        model.rightLeg.visible = visible;
        model.leftLeg.visible = visible;
    }

    private static void setAllPartsVisible(HumanoidModel<?> model) {
        setAllVisible(model, true);
        makeAllVisible(model.head);
        makeAllVisible(model.body);
        makeAllVisible(model.rightArm);
        makeAllVisible(model.leftArm);
        makeAllVisible(model.rightLeg);
        makeAllVisible(model.leftLeg);
    }

    private static void makeAllVisible(ModelPart part) {
        part.visible = true;
        part.getAllParts().forEach(p -> p.visible = true);
    }

    private static ResourceLocation armorResource(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        ArmorItem armor = (ArmorItem) stack.getItem();
        try {
            String path = armor.getArmorTexture(stack, entity, slot, type);
            if (path != null && !path.isEmpty()) {
                try {
                    return new ResourceLocation(path);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        String material = armor.getMaterial().getName();
        if (material.contains(":")) {
            material = material.substring(material.lastIndexOf(':') + 1);
        }
        material = switch (material.toLowerCase()) {
            case "leather" -> "leather";
            case "chainmail", "chain" -> "chainmail";
            case "iron" -> "iron";
            case "gold", "golden" -> "gold";
            case "diamond" -> "diamond";
            case "netherite" -> "netherite";
            default -> material;
        };
        String layer = slot == EquipmentSlot.LEGS ? "layer_2" : "layer_1";
        String suffix = type == null || type.isEmpty() ? "" : "_" + type;
        return ResourceLocation.tryParse("minecraft:textures/models/armor/" + material + "_" + layer + suffix + ".png");
    }

    private static void ensureModels() {
        if (armorInner == null) {
            var models = Minecraft.getInstance().getEntityModels();
            armorInner = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
            armorOuter = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        }
    }

    // ---------------- 饰品 ----------------

    private record Worn(String id, int index, boolean cosmetic, boolean renders, ItemStack stack) {
    }

    private static void renderCurios(EntityNPCInterface npc, PoseStack ps, MultiBufferSource buffer, int light,
                                     float partialTick, float[][] trs) {
        float[] torso = trs[TORSO];
        if (torso == null) return;
        List<Worn> worn = collectWorn(npc);
        if (worn.isEmpty()) return;
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(npc);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> ler)) return;
        EntityModel<?> model = ler.getModel();
        if (!(model instanceof HumanoidModel<?> body)) return;
        @SuppressWarnings("unchecked")
        EntityRenderer<EntityNPCInterface> raw = (EntityRenderer<EntityNPCInterface>) (EntityRenderer<?>) renderer;
        ResourceLocation texture = raw.getTextureLocation(npc);
        SavedPose saved = savePose(body);
        ps.pushPose();
        try {
            QUAT.set(torso[3], torso[4], torso[5], torso[6]).rotateZ((float) Math.PI);
            ps.mulPose(QUAT);
            ps.translate(0, PIVOT_BL[TORSO][1], 0);
            poseFromPhysics(body, trs);
            RenderLayerParent<LivingEntity, EntityModel<LivingEntity>> parent = new RenderLayerParent<>() {
                @SuppressWarnings("unchecked")
                @Override
                public EntityModel<LivingEntity> getModel() {
                    return (EntityModel<LivingEntity>) body;
                }

                @Override
                public ResourceLocation getTextureLocation(LivingEntity e) {
                    return texture;
                }
            };
            float ageInTicks = npc.tickCount + partialTick;
            for (Worn w : worn) {
                Optional<ICurioRenderer> opt = CuriosRendererRegistry.getRenderer(w.stack().getItem());
                if (opt.isPresent()) {
                    opt.get().render(w.stack(), new SlotContext(w.id(), npc, w.index(), w.cosmetic(), w.renders()),
                            ps, parent, buffer, light, 0, 0, partialTick, ageInTicks, 0, 0);
                } else if (RagdollifiedBridge.isGeckoLibArmor(w.stack().getItem())) {
                    renderGeckoCurio(w.stack(), ps, buffer, light, npc, body);
                }
            }
        } finally {
            ps.popPose();
            restorePose(body, saved);
        }
    }

    private static List<Worn> collectWorn(EntityNPCInterface npc) {
        List<Worn> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(npc).ifPresent(handler -> {
            for (var entry : handler.getCurios().entrySet()) {
                String id = entry.getKey();
                var sh = entry.getValue();
                var stacks = sh.getStacks();
                var cosmetics = sh.getCosmeticStacks();
                List<Boolean> renders = sh.getRenders();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    boolean renderStatus = renders.size() > i && Boolean.TRUE.equals(renders.get(i));
                    ItemStack stack = cosmetics.getStackInSlot(i);
                    boolean cosmetic = true;
                    if (stack.isEmpty() && renderStatus) {
                        stack = stacks.getStackInSlot(i);
                        cosmetic = false;
                    }
                    if (!stack.isEmpty()) {
                        out.add(new Worn(id, i, cosmetic, renderStatus, stack.copy()));
                    }
                }
            }
        });
        return out;
    }

    private static void renderGeckoCurio(ItemStack stack, PoseStack ps, MultiBufferSource buffer, int light,
                                         LivingEntity wearer, HumanoidModel<?> baseModel) {
        if (stack.isEmpty()) return;
        EquipmentSlot slot = stack.getItem() instanceof ArmorItem a ? a.getEquipmentSlot() : EquipmentSlot.CHEST;
        ps.pushPose();
        try {
            RagdollifiedBridge.renderGeckoLibArmor(stack, slot, wearer, ps, buffer, light, baseModel);
        } finally {
            ps.popPose();
        }
    }

    // ---------------- 姿态（照抄 ragdollified poseFromPhysics） ----------------

    private record SavedPose(float[] data, boolean young, boolean riding, boolean crouching) {
    }

    private static ModelPart[] parts(HumanoidModel<?> m) {
        return new ModelPart[]{m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
    }

    private static SavedPose savePose(HumanoidModel<?> m) {
        ModelPart[] ps = parts(m);
        float[] data = new float[ps.length * 6];
        for (int i = 0; i < ps.length; i++) {
            ModelPart p = ps[i];
            int o = i * 6;
            data[o] = p.x;
            data[o + 1] = p.y;
            data[o + 2] = p.z;
            data[o + 3] = p.xRot;
            data[o + 4] = p.yRot;
            data[o + 5] = p.zRot;
        }
        return new SavedPose(data, m.young, m.riding, m.crouching);
    }

    private static void restorePose(HumanoidModel<?> m, SavedPose s) {
        ModelPart[] ps = parts(m);
        for (int i = 0; i < ps.length; i++) {
            ModelPart p = ps[i];
            int o = i * 6;
            p.setPos(s.data()[o], s.data()[o + 1], s.data()[o + 2]);
            p.xRot = s.data()[o + 3];
            p.yRot = s.data()[o + 4];
            p.zRot = s.data()[o + 5];
        }
        m.young = s.young();
        m.riding = s.riding();
        m.crouching = s.crouching();
    }

    private static void poseFromPhysics(HumanoidModel<?> model, float[][] trs) {
        model.young = false;
        model.riding = false;
        model.crouching = false;
        posePart(model.head, HEAD, trs);
        posePart(model.hat, HEAD, trs);
        posePart(model.body, TORSO, trs);
        posePart(model.leftArm, LEFT_ARM, trs);
        posePart(model.rightArm, RIGHT_ARM, trs);
        posePart(model.leftLeg, LEFT_LEG, trs);
        posePart(model.rightLeg, RIGHT_LEG, trs);
    }

    private static void posePart(ModelPart part, int partIndex, float[][] trs) {
        if (part == null) return;
        float[] tr = trs[partIndex];
        float[] torso = trs[TORSO];
        if (tr == null || torso == null) {
            part.setPos(0, 0, 0);
            part.xRot = 0;
            part.yRot = 0;
            part.zRot = 0;
            return;
        }
        Quaternionf flip = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf rootRot = new Quaternionf(torso[3], torso[4], torso[5], torso[6]).mul(flip);
        Quaternionf partRot = new Quaternionf(tr[3], tr[4], tr[5], tr[6]).mul(flip);
        Vector3f torsoPivot = new Vector3f(PIVOT_BL[TORSO]);
        rootRot.transform(torsoPivot);
        Vector3f root = new Vector3f(torso[0] + torsoPivot.x, torso[1] + torsoPivot.y, torso[2] + torsoPivot.z);
        Vector3f pivot = new Vector3f(PIVOT_BL[partIndex]);
        partRot.transform(pivot);
        pivot.add(tr[0], tr[1], tr[2]).sub(root);
        new Quaternionf(rootRot).conjugate().transform(pivot);
        part.setPos(pivot.x * 16f, pivot.y * 16f, pivot.z * 16f);
        Vector3f euler = new Quaternionf(rootRot).conjugate().mul(partRot).getEulerAnglesZYX(new Vector3f());
        part.xRot = euler.x;
        part.yRot = euler.y;
        part.zRot = euler.z;
    }
}
