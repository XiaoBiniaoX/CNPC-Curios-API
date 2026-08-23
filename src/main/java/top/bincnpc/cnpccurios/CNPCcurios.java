package top.bincnpc.cnpccurios;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import top.bincnpc.cnpccurios.event.CuriosEventHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CNPC饰品栏 (CNPC Curios)
 * 为 CustomNPCs 实体添加 Curios 饰品栏支持。
 *
 * 功能：
 * 1. NPC物品编辑界面中显示与玩家对等的饰品槽位（竖排，盔甲栏坐标）
 * 2. NPC实体自动渲染佩戴的饰品
 * 3. NPC可以吃到饰品提供的属性加成
 * 4. 提供 CNPC 脚本 API（top.bincnpc.cnpccurios.api.CNPCcuriosAPI）
 *
 * 1.21.1 NeoForge 说明：
 * Curios 9.5.1 在 RegisterCapabilitiesEvent 里对「全部」EntityType 注册饰品栏 capability，
 * 唯一条件是 CuriosApi.getEntitySlots(entity) 非空（Curios.java:122-148），没有 Player 限制。
 * 因此只要数据包 data/cnpccurios/curios/entities/cnpc_entities.json 给 CNPC 实体配了槽位，
 * capability 就会自动附加 —— 本 mod 无需编写任何 capability 注册代码。
 */
@Mod(CNPCcurios.MODID)
public class CNPCcurios {
    public static final String MODID = "cnpccurios";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * slot在全容器中的index → curio槽位类型ID
     * （由 MixinContainerNPCInv 注入槽位时写入，MixinGuiNPCInv 绘制标签时读取）
     *
     * 用 ConcurrentHashMap 而非 LinkedHashMap：单机时容器构造发生在服务端线程，
     * 而 GUI 绘制发生在客户端渲染线程，两侧同时读写同一个静态 map 会有并发风险。
     * 只是「index → 槽位名」的展示用映射，用并发 map 最省事且安全。
     */
    public static final Map<Integer, String> SLOT_INDEX_TO_ID = new ConcurrentHashMap<>();

    public static String getSlotTypeId(int slotIndex) {
        return SLOT_INDEX_TO_ID.get(slotIndex);
    }

    /**
     * NeoForge 主类构造：注入 mod 事件总线与 ModContainer。
     * （1.20.1 Forge 用 FMLJavaModLoadingContext.get()，1.21.1 改为构造参数注入）
     */
    public CNPCcurios(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置文件（NeoForge 的 registerConfig 挂在 ModContainer 上）
        CnpcCuriosConfig.register(modContainer);

        // 注册游戏事件处理器（饰品掉落规则）
        NeoForge.EVENT_BUS.register(new CuriosEventHandler());

        LOGGER.info("CNPC饰品栏 (CNPC Curios) 已加载！");
    }
}
