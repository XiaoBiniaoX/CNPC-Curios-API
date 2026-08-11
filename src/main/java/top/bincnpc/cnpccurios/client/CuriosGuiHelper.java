package top.bincnpc.cnpccurios.client;

import noppes.npcs.entity.EntityNPCInterface;

public class CuriosGuiHelper {
    public static void openCuriosScreen(EntityNPCInterface npc) {
        if (npc == null) return;
        MinecraftReflect.setScreen(MinecraftReflect.getMinecraft(),
                ScreenNPCCurios.of(new ContainerNPCCurios(npc)));
    }
}
