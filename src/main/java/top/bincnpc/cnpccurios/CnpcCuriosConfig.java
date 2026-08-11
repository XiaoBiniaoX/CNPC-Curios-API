package top.bincnpc.cnpccurios;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 饰品栏客户端配置。生成 config/cnpccurios-client.toml
 */
public class CnpcCuriosConfig {

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.IntValue SLOT_X;
    public static final ForgeConfigSpec.IntValue SLOT_Y;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("饰品栏位置");
        SLOT_X = b.comment("X偏移(相对GUI左上角)").defineInRange("slotX", 250, -500, 500);
        SLOT_Y = b.comment("Y偏移(相对GUI左上角)").defineInRange("slotY", 50, -500, 500);
        b.pop();
        CLIENT_SPEC = b.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}