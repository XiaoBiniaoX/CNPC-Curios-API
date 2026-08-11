package top.bincnpc.cnpccurios;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import top.bincnpc.cnpccurios.event.CuriosEventHandler;
import top.bincnpc.cnpccurios.network.SyncCuriosPacket;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CNPC饰品栏 (CNPC Curios)
 * 为 CustomNPCs 实体添加 Curios 饰品栏支持。
 *
 * 功能：
 * 1. NPC物品编辑界面中显示与玩家对等的饰品槽位（竖排，盔甲栏坐标）
 * 2. NPC实体自动渲染佩戴的饰品
 * 3. NPC可以吃到饰品提供的属性加成
 * 4. 提供 CNPC 脚本 API（top.bincnpc.cnpccurios.api.CNPCcuriosAPI）
 */
@Mod(CNPCcurios.MODID)
public class CNPCcurios {
    public static final String MODID = "cnpccurios";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** slot在全容器中的index → curio槽位类型ID */
    public static final Map<Integer, String> SLOT_INDEX_TO_ID = new LinkedHashMap<>();

    public static String getSlotTypeId(int slotIndex) {
        return SLOT_INDEX_TO_ID.get(slotIndex);
    }

    public CNPCcurios() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册网络包
        SyncCuriosPacket.register();

        // 注册配置文件
        CnpcCuriosConfig.register();

        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(new CuriosEventHandler());

        LOGGER.info("CNPC饰品栏 (CNPC Curios) 已加载！");
    }
}
