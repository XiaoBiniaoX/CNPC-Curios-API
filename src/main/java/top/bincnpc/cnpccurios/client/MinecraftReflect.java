package top.bincnpc.cnpccurios.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;

/**
 * 最小化反射工具。只封装 getInstance 和 setScreen。
 */
public class MinecraftReflect {

    private static Method sGetInstance;
    private static Method sSetScreen;

    public static Minecraft getMinecraft() {
        try {
            if (sGetInstance == null) {
                for (String n : new String[]{"getInstance", "m_91087_"}) {
                    try { sGetInstance = Minecraft.class.getMethod(n); break; }
                    catch (NoSuchMethodException ignored) {}
                }
            }
            return (Minecraft) sGetInstance.invoke(null);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static void setScreen(Minecraft mc, Screen screen) {
        try {
            if (sSetScreen == null) {
                for (String n : new String[]{"setScreen", "m_91152_"}) {
                    try { sSetScreen = Minecraft.class.getMethod(n, Screen.class); break; }
                    catch (NoSuchMethodException ignored) {}
                }
            }
            sSetScreen.invoke(mc, screen);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
