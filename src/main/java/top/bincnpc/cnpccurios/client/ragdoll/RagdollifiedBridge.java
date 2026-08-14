package top.bincnpc.cnpccurios.client.ragdoll;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

/**
 * ragdollified 可选依赖的纯反射桥接。
 * 全部调用都做防御性 try/catch，任何失败都静默降级（可选依赖不得影响主 mod）。
 */
public final class RagdollifiedBridge {

    private static boolean checked;
    private static boolean available;

    // ClientRagdollManager
    private static Method mGetAll;
    // ClientRagdoll
    private static Method mGetOriginalEntityId;
    private static Method mGetId;
    private static Method mIsDestroyed;
    private static Method mIsSettledOnLiquid;
    private static Method mGetSmoothedTransform;
    // RagdollifiedConfig
    private static Method mGetArmorDistSq;
    // GeckoLibArmorHelper
    private static Method mIsGeckoArmor;
    private static Method mRenderGeckoArmor;
    // RagdollTransform 字段（javax.vecmath 通过反射读取，避免直接引用 ragdollified 内嵌的 vecmath 类）
    private static Field fPosition;
    private static Field fRotation;
    private static Field fVecX, fVecY, fVecZ;
    private static Field fQuatX, fQuatY, fQuatZ, fQuatW;

    private RagdollifiedBridge() {
    }

    public static boolean isAvailable() {
        if (!checked) {
            init();
        }
        return available;
    }

    private static void init() {
        checked = true;
        if (!ModList.get().isLoaded("ragdollified")) {
            return;
        }
        try {
            Class<?> cManager = Class.forName("com.raiiiden.ragdollified.client.ClientRagdollManager");
            Class<?> cRagdoll = Class.forName("com.raiiiden.ragdollified.client.ClientRagdoll");
            Class<?> cTransform = Class.forName("com.raiiiden.ragdollified.RagdollTransform");
            mGetAll = cManager.getMethod("getAll");
            mGetOriginalEntityId = cRagdoll.getMethod("getOriginalEntityId");
            mGetId = cRagdoll.getMethod("getId");
            mIsDestroyed = cRagdoll.getMethod("isDestroyed");
            mIsSettledOnLiquid = cRagdoll.getMethod("isSettledOnLiquid");
            mGetSmoothedTransform = cRagdoll.getMethod("getSmoothedTransform", int.class);
            Class<?> cConfig = Class.forName("com.raiiiden.ragdollified.config.RagdollifiedConfig");
            mGetArmorDistSq = cConfig.getMethod("getArmorRenderDistanceSq");
            try {
                Class<?> cGecko = Class.forName("com.raiiiden.ragdollified.client.compat.GeckoLibArmorHelper");
                mIsGeckoArmor = cGecko.getMethod("isGeckoLibArmor", Item.class);
                mRenderGeckoArmor = cGecko.getMethod("renderGeckoLibArmor", ItemStack.class, EquipmentSlot.class,
                        Entity.class, PoseStack.class, MultiBufferSource.class, int.class, int.class, HumanoidModel.class);
            } catch (Throwable ignored) {
            }
            fPosition = cTransform.getField("position");
            fRotation = cTransform.getField("rotation");
            Class<?> vecmathVector = Class.forName("javax.vecmath.Vector3f");
            fVecX = vecmathVector.getField("x");
            fVecY = vecmathVector.getField("y");
            fVecZ = vecmathVector.getField("z");
            Class<?> vecmathQuat = Class.forName("javax.vecmath.Quat4f");
            fQuatX = vecmathQuat.getField("x");
            fQuatY = vecmathQuat.getField("y");
            fQuatZ = vecmathQuat.getField("z");
            fQuatW = vecmathQuat.getField("w");
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    public static Collection<?> allRagdolls() {
        if (!isAvailable()) return Collections.emptyList();
        try {
            Object r = mGetAll.invoke(null);
            if (r instanceof Collection<?> c) return c;
        } catch (Throwable ignored) {
        }
        return Collections.emptyList();
    }

    public static int originalEntityId(Object ragdoll) {
        if (!isAvailable()) return -1;
        try {
            return (int) mGetOriginalEntityId.invoke(ragdoll);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static boolean isDestroyed(Object ragdoll) {
        if (!isAvailable()) return true;
        try {
            return (boolean) mIsDestroyed.invoke(ragdoll);
        } catch (Throwable t) {
            return true;
        }
    }

    /** 与 ragdollified 相同的液体浮沉偏移 */
    public static float liquidBob(Object ragdoll) {
        if (!isAvailable()) return 0.0f;
        try {
            if (!(boolean) mIsSettledOnLiquid.invoke(ragdoll)) return 0.0f;
            long now = System.currentTimeMillis();
            float phase = ((int) mGetId.invoke(ragdoll) & 0xFF) * 0.0246f;
            return (float) (Math.sin(now * 0.0044 + phase) * 0.06);
        } catch (Throwable t) {
            return 0.0f;
        }
    }

    /** 返回 {px,py,pz,qx,qy,qz,qw}，partIndex 为 RagdollPart.ordinal()（TORSO=0..RIGHT_ARM=5） */
    public static float[] smoothedTransform(Object ragdoll, int partIndex) {
        if (!isAvailable()) return null;
        try {
            Object tr = mGetSmoothedTransform.invoke(ragdoll, partIndex);
            if (tr == null) return null;
            Object pos = fPosition.get(tr);
            Object rot = fRotation.get(tr);
            if (pos == null || rot == null) return null;
            return new float[]{
                    fVecX.getFloat(pos), fVecY.getFloat(pos), fVecZ.getFloat(pos),
                    fQuatX.getFloat(rot), fQuatY.getFloat(rot), fQuatZ.getFloat(rot), fQuatW.getFloat(rot)
            };
        } catch (Throwable t) {
            return null;
        }
    }

    /** 护甲渲染距离平方（默认 100²） */
    public static double armorDistSq() {
        if (!isAvailable()) return 10000.0;
        try {
            return (double) mGetArmorDistSq.invoke(null);
        } catch (Throwable t) {
            return 10000.0;
        }
    }

    /** 布娃娃本体渲染距离平方（默认 128 格），饰品应跟随本体而非护甲距离。 */
    public static double renderDistSq() {
        try {
            Class<?> config = Class.forName("com.raiiiden.ragdollified.config.RagdollifiedConfig");
            Object value = config.getField("RENDER_DISTANCE").get(null);
            double distance = ((Number) value.getClass().getMethod("get").invoke(value)).doubleValue();
            return distance * distance;
        } catch (Throwable t) {
            return 16384.0;
        }
    }

    public static boolean isGeckoLibArmor(Item item) {
        if (mIsGeckoArmor == null) return false;
        try {
            return (boolean) mIsGeckoArmor.invoke(null, item);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 用真实实体（而非 ragdollified 的 null/代理 ArmorStand）渲染 GeckoLib 护甲 */
    public static void renderGeckoLibArmor(ItemStack stack, EquipmentSlot slot, Entity entity,
                                           PoseStack poseStack, MultiBufferSource buffer, int light,
                                           HumanoidModel<?> baseModel) {
        if (mRenderGeckoArmor == null) return;
        try {
            mRenderGeckoArmor.invoke(null, stack, slot, entity, poseStack, buffer, light,
                    OverlayTexture.NO_OVERLAY, baseModel);
        } catch (Throwable ignored) {
        }
    }
}
