package top.bincnpc.cnpccurios.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import noppes.npcs.shared.client.gui.components.GuiBasicContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.bincnpc.cnpccurios.CNPCcurios;

/**
 * 给注入进来的饰品槽绘制槽位背景与类型标签。
 *
 * 1.21.1 相比 1.20.1 的两处简化（都有实证，不是猜）：
 * 1. 注入目标从混淆名 m_7286_ 改为明文 renderBg
 *    （CNPC1.21.1反编译 GuiNPCInv.java:109 protected void renderBg(GuiGraphics, float, int, int)），
 *    因为 NeoForge 1.21.1 运行时直接用 mojmap，全部混入 remap=false。
 * 2. guiLeft / guiTop 是 public 字段（GuiBasicContainer.java:67-68），menu 来自
 *    AbstractContainerScreen，slots 是 AbstractContainerMenu 的 public final 字段。
 *    因此 1.20.1 版里那三处反射（f_97732_/menu、f_38839_/slots、getField("guiLeft")）全部删除。
 *
 * 教训 L4：guiLeft/guiTop 是「继承来的」字段，@Shadow 解析不到会让整个目标类的 mixin 被撤销，
 * 所以这里用 (GuiBasicContainer)(Object)this 强转访问，不用 @Shadow。
 */
@Mixin(value = GuiNPCInv.class, remap = false)
public abstract class MixinGuiNPCInv {

    /** CNPC 原始 52 槽，索引 >= 52 的都是本 mod 追加的饰品槽 */
    @Unique
    private static final int ORIG_SLOTS = 52;

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void cnpccurios$onRenderBg(GuiGraphics g, float partialTicks, int mouseX, int mouseY, CallbackInfo ci) {
        GuiNPCInv self = (GuiNPCInv) (Object) this;
        GuiBasicContainer<?> base = (GuiBasicContainer<?>) (Object) this;

        if (self.getMenu() == null) return;
        var slots = self.getMenu().slots;
        // 没有追加过饰品槽就什么都不画
        if (slots == null || slots.size() <= ORIG_SLOTS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;

        int guiLeft = base.guiLeft;
        int guiTop = base.guiTop;

        RenderSystem.enableBlend();
        for (int i = ORIG_SLOTS; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s == null) continue;

            int sx = guiLeft + s.x;
            int sy = guiTop + s.y;

            // 槽位底色 + 上/左亮边 + 下/右暗边 + 内阴影，模拟原版槽位凹陷效果
            g.fill(sx, sy, sx + 16, sy + 16, 0x80000000);
            g.fill(sx, sy, sx + 16, sy + 1, 0x60FFFFFF);
            g.fill(sx, sy, sx + 1, sy + 16, 0x60FFFFFF);
            g.fill(sx + 15, sy, sx + 16, sy + 16, 0x60333333);
            g.fill(sx, sy + 15, sx + 16, sy + 16, 0x60333333);
            g.fill(sx + 1, sy + 1, sx + 15, sy + 2, 0x40000000);
            g.fill(sx + 1, sy + 1, sx + 2, sy + 15, 0x40000000);

            // 槽位类型名前 4 个字符，作为槽位用途提示
            String typeId = CNPCcurios.getSlotTypeId(i);
            if (typeId != null && !typeId.isEmpty()) {
                String label = typeId.length() > 4 ? typeId.substring(0, 4) : typeId;
                g.drawString(mc.font, label, sx + 1, sy + 10, 0x80FFFFFF, false);
            }
        }
        RenderSystem.disableBlend();
    }
}
