package top.bincnpc.cnpccurios.mixin;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.bincnpc.cnpccurios.NpcSlotInheritance;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.common.data.CuriosEntityManager;

import java.util.Map;

/**
 * 让 CNPC 的 NPC 自动继承「玩家拥有的全部饰品槽位」。
 *
 * ── 为什么需要这个（哈基彬实测：泰拉饰品在 NPC 身上渲染不出来）──
 * 取证结论：不是渲染代码的问题，是槽位根本不存在。
 * 各饰品 mod 自己决定把槽位发给谁，测试包实测：
 *   terra_curio  data/terra_curio/curios/entities/common.json → {"entities":["player"],"slots":["accessory"]}
 *   twilightforest add_slots_tf.json                          → {"entities":["player"],"slots":["head","charm"]}
 *   artifacts    slots.json → player + 僵尸/骷髅/猪灵等 12 种  → head/necklace/belt/hands/feet
 * 所以「奇异饰品」能在 NPC 上渲染（它自己给一堆生物发了槽位，槽位名又和我方重叠），
 * 而「泰拉饰品」的 accessory 槽 NPC 完全没有 → 放不进去，自然也渲染不出来。
 *
 * 另已排除两个方向（有实证，不是猜）：
 *   1. terra_curio 那 10 个饰品渲染器全是 <T extends LivingEntity> 泛型 + ICurioRenderer.followBodyRotations，
 *      javap 逐个确认 Player 引用数 = 0，渲染代码本身完全支持任意生物。
 *   2. 渲染器注册是按物品注册的（CuriosRendererRegistry.register(Item, Supplier)），与实体无关。
 *
 * ── 为什么注入这里 ──
 * CuriosEntityManager.getEntitySlots(EntityType) 是**唯一汇聚点**，
 * capability 附加、物品校验、GUI、渲染四条链最终都查它：
 *   - capability：Curios.java:126/138 registerEntity 判 !getEntitySlots(livingEntity).isEmpty()
 *   - 物品校验：CuriosImplMixinHooks.isStackValid:135 → getItemStackSlots(stack, entity)
 *               → filteredSlots(..., CuriosApi.getEntitySlots(livingEntity))（同文件 :98-105）
 *   - 调用路径：CuriosApi.getEntitySlots → CuriosImplMixinHooks.getEntitySlots:84-87 → 本方法
 * 在这一个点补槽位，四条链一起生效，无需分别 hook，也无需改数据包。
 *
 * 好处：以后哈基彬装任何新饰品 mod（铁魔法、MM、其他），只要它给玩家发了槽位，
 * NPC 自动就有同样的槽位，不需要我再改 json 或重新发版。
 */
@Mixin(value = CuriosEntityManager.class, remap = false)
public abstract class MixinCuriosEntityManager {

    @Inject(method = "getEntitySlots", at = @At("RETURN"), cancellable = true)
    private void cnpccurios$inheritPlayerSlots(EntityType<?> type,
                                               CallbackInfoReturnable<Map<String, ISlotType>> cir) {
        // 只对 CNPC 的 NPC 实体类型生效，绝不影响其他 mod 的生物与玩家本身
        if (!NpcSlotInheritance.isNpcType(type)) return;

        Map<String, ISlotType> original = cir.getReturnValue();
        // this 是 SERVER 或 CLIENT 两个实例之一；用哪个实例查玩家槽位就必须用同一个实例，
        // 否则服务端会读到客户端的空表（两侧 entitySlots 是各自独立的字段）
        Map<String, ISlotType> merged = NpcSlotInheritance.merge(
                (CuriosEntityManager) (Object) this, original);

        if (merged != null) {
            cir.setReturnValue(merged);
        }
    }
}
