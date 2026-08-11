package top.bincnpc.cnpccurios.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import noppes.npcs.entity.EntityNPCInterface;

public class ScreenNPCCurios extends AbstractContainerScreen<ContainerNPCCurios> {

    private static final ResourceLocation BG =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");

    private ScreenNPCCurios(ContainerNPCCurios c, Component title) {
        super(c, new Inventory(null), title);
        this.imageWidth = 176;
        this.imageHeight = 222;
    }

    public static ScreenNPCCurios of(ContainerNPCCurios c) {
        EntityNPCInterface npc = c.getNpc();
        Component t = npc != null
                ? Component.literal(npc.getDisplayName().getString() + " - 饰品栏")
                : Component.empty();
        return new ScreenNPCCurios(c, t);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g); super.render(g, mx, my, pt); this.renderTooltip(g, mx, my);
    }

    @Override protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        g.blit(BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override public boolean isPauseScreen() { return false; }
}
