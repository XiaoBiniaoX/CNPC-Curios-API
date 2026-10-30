package top.bincnpc.cnpccurios.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/** NPC 专用 Curios 层；逐件隔离渲染，玩家与其他实体的原生 CuriosLayer 不受影响。 */
public final class SafeNPCCuriosLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {
    private final RenderLayerParent<T, M> parent;

    public SafeNPCCuriosLayer(RenderLayerParent<T, M> parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, T npc, float limbSwing,
                       float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        RenderIsolation.run(npc, "Curios inventory", getParentModel(), pose, isolated ->
                CuriosApi.getCuriosInventory(npc).ifPresent(handler -> {
                    for (var entry : handler.getCurios().entrySet()) {
                        var stacks = entry.getValue();
                        if (stacks == null || stacks.getStacks() == null) continue;
                        for (int i = 0; i < stacks.getStacks().getSlots(); i++) {
                            final int index = i;
                            RenderIsolation.run(npc, entry.getKey() + "[" + i + "]",
                                    getParentModel(), isolated, slotPose -> {
                                        var renders = stacks.getRenders();
                                        boolean visible = index < renders.size()
                                                && Boolean.TRUE.equals(renders.get(index));
                                        var cosmetics = stacks.getCosmeticStacks();
                                        ItemStack stack = index < cosmetics.getSlots()
                                                ? cosmetics.getStackInSlot(index) : ItemStack.EMPTY;
                                        boolean cosmetic = !stack.isEmpty();
                                        if (!cosmetic && visible) {
                                            stack = stacks.getStacks().getStackInSlot(index);
                                        }
                                        if (stack.isEmpty()) return;

                                        ItemStack worn = stack;
                                        SlotContext context = new SlotContext(entry.getKey(), npc,
                                                index, cosmetic, visible);
                                        CuriosRendererRegistry.getRenderer(worn.getItem()).ifPresent(renderer ->
                                                renderer.render(worn, context, slotPose, parent, buffer,
                                                        light, limbSwing, limbSwingAmount, partialTick,
                                                        age, yaw, pitch));
                                    });
                        }
                    }
                }));
    }
}
