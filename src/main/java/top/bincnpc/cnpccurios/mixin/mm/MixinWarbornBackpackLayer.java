package top.bincnpc.cnpccurios.mixin.mm;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 让WN背包在"curio"槽位也能渲染。
 * 使用反射调用WN渲染器以避免编译期类依赖。
 */
@Pseudo
@Mixin(targets = "com.raiiiden.warborn.client.renderer.layer.WarbornBackpackLayer", remap = false)
public abstract class MixinWarbornBackpackLayer {

    @Unique private static Class<?> backpackItemClass;
    @Unique private static Class<?> backpackRendererClass;
    @Unique private static Method getTextureLocation;
    @Unique private static Class<?> renderTypeClass;
    @Unique private static Method armorCutoutNoCull;

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
                                LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTick,
                                float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        try {
            initReflection();

            List<SlotResult> curioBackpacks = CuriosApi.getCuriosHelper().findCurios(entity,
                    stack -> backpackItemClass.isInstance(stack.getItem()));

            for (SlotResult slotResult : curioBackpacks) {
                if ("curio".equals(slotResult.slotContext().identifier()) && slotResult.slotContext().visible()) {
                    renderBackpackReflect(poseStack, bufferSource, packedLight, entity,
                            slotResult.stack());
                }
            }
        } catch (Throwable t) {
            System.err.println("[CNPCcurios] WN backpack curio render: " + t);
        }
    }

    @Unique
    private static void initReflection() throws ClassNotFoundException, NoSuchMethodException {
        if (backpackItemClass == null) {
            backpackItemClass = Class.forName("com.raiiiden.warborn.common.item.BackpackItem");
            backpackRendererClass = Class.forName("com.raiiiden.warborn.client.renderer.armor.WarbornBackpackRenderer");
            getTextureLocation = backpackRendererClass.getMethod("getTextureLocation",
                    Class.forName("net.minecraft.world.item.ArmorItem"));
            renderTypeClass = Class.forName("net.minecraft.client.renderer.RenderType");
            armorCutoutNoCull = renderTypeClass.getMethod("armorCutoutNoCull",
                    Class.forName("net.minecraft.resources.ResourceLocation"));
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderBackpackReflect(PoseStack poseStack, MultiBufferSource bufferSource,
                                        int packedLight, LivingEntity entity, ItemStack stack) {
        try {
            Object backpackItem = stack.getItem();
            Constructor<?> ctor = backpackRendererClass.getConstructor(backpackItemClass);
            Object renderer = ctor.newInstance(backpackItem);

            // copyPropertiesTo
            var parentModel = ((net.minecraft.client.renderer.entity.layers.RenderLayer)(Object)this).getParentModel();
            parentModel.getClass().getMethod("copyPropertiesTo",
                    Class.forName("net.minecraft.client.model.EntityModel")).invoke(parentModel, renderer);

            // prepForRender
            renderer.getClass().getMethod("prepForRender", LivingEntity.class, ItemStack.class,
                    EquipmentSlot.class, Class.forName("net.minecraft.client.model.EntityModel"))
                    .invoke(renderer, entity, stack, EquipmentSlot.CHEST, renderer);

            // getTextureLocation → ResourceLocation
            Object tex = getTextureLocation.invoke(renderer, backpackItem);

            // RenderType.armorCutoutNoCull(tex)
            Object renderType = armorCutoutNoCull.invoke(null, tex);

            // renderToBuffer
            renderer.getClass().getMethod("renderToBuffer", PoseStack.class,
                    Class.forName("com.mojang.blaze3d.vertex.VertexConsumer"),
                    int.class, int.class, float.class, float.class, float.class, float.class)
                    .invoke(renderer, poseStack,
                            bufferSource.getBuffer((net.minecraft.client.renderer.RenderType) renderType),
                            packedLight, 15728880, 1.0F, 1.0F, 1.0F, 1.0F);

        } catch (Throwable t) {
            System.err.println("[CNPCcurios] WN backpack curio render failed: " + t);
        }
    }
}
