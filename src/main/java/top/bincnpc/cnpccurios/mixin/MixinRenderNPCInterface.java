package top.bincnpc.cnpccurios.mixin;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.client.render.CuriosLayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * 在 RenderNPCInterface 构造末尾注入渲染层。
 * 1. CuriosLayer - 标准的Curios饰品渲染
 * 2. WN (WARBORN) 渲染层 - 背包/护肩/制服/面具（仅当WN mod存在时）
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public abstract class MixinRenderNPCInterface {

    @Unique private static Method sAddLayer;
    @Unique private static boolean wnChecked;
    @Unique private static boolean wnAvailable;

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/model/EntityModel;F)V",
            at = @At("RETURN"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void onConstructed(EntityRendererProvider.Context manager, EntityModel model, float shadowSize, CallbackInfo ci) {
        try {
            if (sAddLayer == null) {
                try { sAddLayer = LivingEntityRenderer.class.getDeclaredMethod("addLayer", RenderLayer.class); }
                catch (NoSuchMethodException e) {
                    sAddLayer = LivingEntityRenderer.class.getDeclaredMethod("m_115326_", RenderLayer.class);
                }
                sAddLayer.setAccessible(true);
            }

            // 1. 注入 CuriosLayer（标准Curios饰品渲染）
            sAddLayer.invoke(this, new CuriosLayer((LivingEntityRenderer) (Object) this));

            // 2. 注入 WN 渲染层（仅当WN mod存在时）
            injectWNLayers();

        } catch (Throwable t) {
            System.err.println("[CNPCcurios] addLayer(render) failed: " + t);
        }
    }

    @Unique
    private void injectWNLayers() {
        if (!isWNAvailable()) {
            System.out.println("[CNPCcurios] WN mod not detected, skip WN render layers");
            return;
        }
        System.out.println("[CNPCcurios] WN mod detected, injecting render layers...");

        String[] layerClasses = {
            "com.raiiiden.warborn.client.renderer.layer.WarbornBackpackLayer",
            "com.raiiiden.warborn.client.renderer.layer.WarbornShoulderpadsLayer",
            "com.raiiiden.warborn.client.renderer.layer.WarbornUniformLayer",
            "com.raiiiden.warborn.client.renderer.layer.WarbornMaskLayer"
        };

        for (String className : layerClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                // 遍历构造器找单参数的那个（不硬编码RenderLayerParent类名避免Mojang映射问题）
                Constructor<?> matchedCtor = null;
                for (Constructor<?> ctor : clazz.getConstructors()) {
                    if (ctor.getParameterCount() == 1) {
                        matchedCtor = ctor;
                        break;
                    }
                }
                if (matchedCtor == null) {
                    System.err.println("[CNPCcurios] No single-param constructor for " + className);
                    continue;
                }
                Object layer = matchedCtor.newInstance(this);
                sAddLayer.invoke(this, layer);
                System.out.println("[CNPCcurios] WN layer injected: " + className);
            } catch (ClassNotFoundException ignored) {
                System.out.println("[CNPCcurios] WN layer class not found: " + className);
            } catch (Throwable t) {
                System.err.println("[CNPCcurios] WN layer injection failed for " + className + ": " + t);
            }
        }
    }

    @Unique
    private static boolean isWNAvailable() {
        if (!wnChecked) {
            wnChecked = true;
            try {
                Class.forName("com.raiiiden.warborn.WARBORN");
                wnAvailable = true;
            } catch (ClassNotFoundException e) {
                wnAvailable = false;
            }
        }
        return wnAvailable;
    }
}
