package top.bincnpc.cnpccurios.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import top.bincnpc.cnpccurios.CNPCcurios;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

@Mixin(value = GuiNPCInv.class, remap = false)
public abstract class MixinGuiNPCInv {

    @Unique private static final int ORIG_SLOTS = 52;
    @Unique private static Field sMenuField;
    @Unique private static Field sSlotsField;

    @Inject(method = "m_7286_", at = @At("TAIL"))
    private void onRenderBg(GuiGraphics g, float pt, int mx, int my, CallbackInfo ci) {
        try {
            if (sMenuField == null) {
                sMenuField = findFieldInHierarchy(AbstractContainerScreen.class, "f_97732_", "menu");
                sMenuField.setAccessible(true);
            }
            Object menu = sMenuField.get(this);
            if (menu == null) return;

            if (sSlotsField == null) {
                sSlotsField = findFieldInHierarchy(AbstractContainerMenu.class, "f_38839_", "slots");
                sSlotsField.setAccessible(true);
            }
            List<Slot> slots = (List<Slot>) sSlotsField.get(menu);
            if (slots == null || slots.size() <= ORIG_SLOTS) return;

            int gl = getGuiLeft();
            int gt = getGuiTop();
            Minecraft mc = Minecraft.getInstance();

            RenderSystem.enableBlend();
            for (int i = ORIG_SLOTS; i < slots.size(); i++) {
                Slot s = slots.get(i);
                int sx = gl + s.x, sy = gt + s.y;

                g.fill(sx, sy, sx + 16, sy + 16, 0x80000000);
                g.fill(sx, sy, sx + 16, sy + 1, 0x60FFFFFF);
                g.fill(sx, sy, sx + 1, sy + 16, 0x60FFFFFF);
                g.fill(sx + 15, sy, sx + 16, sy + 16, 0x60333333);
                g.fill(sx, sy + 15, sx + 16, sy + 16, 0x60333333);
                g.fill(sx + 1, sy + 1, sx + 15, sy + 2, 0x40000000);
                g.fill(sx + 1, sy + 1, sx + 2, sy + 15, 0x40000000);

                String typeId = CNPCcurios.getSlotTypeId(i);
                if (typeId != null && !typeId.isEmpty()) {
                    String label = typeId.length() > 4 ? typeId.substring(0, 4) : typeId;
                    g.drawString(mc.font, label, sx + 1, sy + 10, 0x80FFFFFF, false);
                }
            }
            RenderSystem.disableBlend();
        } catch (Throwable t) {
            System.err.println("[CNPCcurios] renderBg: " + t);
        }
    }

    @Unique
    private static Field findFieldInHierarchy(Class<?> startClass, String... names) {
        Class<?> clazz = startClass;
        while (clazz != null) {
            for (String name : names) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        throw new RuntimeException("Field not found: " + String.join("/", names)
                + " in hierarchy of " + startClass.getName());
    }

    @Unique
    private int getGuiLeft() throws Exception {
        return getClass().getField("guiLeft").getInt(this);
    }

    @Unique
    private int getGuiTop() throws Exception {
        return getClass().getField("guiTop").getInt(this);
    }
}
