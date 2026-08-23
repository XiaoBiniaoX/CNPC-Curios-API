package top.bincnpc.cnpccurios;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 饰品栏客户端配置。生成 config/cnpccurios-client.toml
 *
 * 1.21.1 差异：ForgeConfigSpec → ModConfigSpec，
 * ModLoadingContext.get().registerConfig → ModContainer.registerConfig。
 */
public class CnpcCuriosConfig {

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.IntValue SLOT_X;
    public static final ModConfigSpec.IntValue SLOT_Y;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("饰品栏位置");
        SLOT_X = b.comment("X偏移(相对GUI左上角)").defineInRange("slotX", 250, -500, 500);
        SLOT_Y = b.comment("Y偏移(相对GUI左上角)").defineInRange("slotY", 50, -500, 500);
        b.pop();
        CLIENT_SPEC = b.build();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    /**
     * 安全读取 X 偏移。
     *
     * 为什么需要这层保护（约法第7条第②项，服务端安全）：
     * 本配置是 CLIENT 类型，专用服务端永远不会加载它。而 MixinContainerNPCInv 注入槽位时
     * 需要读坐标，该 mixin 在服务端也会执行。1.21.1 的 ModConfigSpec.ConfigValue.getRaw()
     * 在 loadedConfig == null 时会 Preconditions.checkState 抛 IllegalStateException
     * （neoforge-21.1.235-sources.jar → ModConfigSpec.java:1232-1236）。
     * 若不保护，服务端注入槽位会整段失败 → 服务端容器 52 槽、客户端 52+N 槽，
     * 槽位数不一致会造成同步错位。
     * 槽位坐标只影响客户端绘制，服务端用默认值即可，不影响功能。
     */
    public static int slotX() {
        return CLIENT_SPEC.isLoaded() ? SLOT_X.get() : SLOT_X.getDefault();
    }

    /** 安全读取 Y 偏移，原因同 {@link #slotX()} */
    public static int slotY() {
        return CLIENT_SPEC.isLoaded() ? SLOT_Y.get() : SLOT_Y.getDefault();
    }
}
