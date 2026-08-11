package top.bincnpc.cnpccurios.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件处理。
 * CuriosLayer 的注册已通过 MixinRenderNPCInterface 在渲染器构造时自动注入，
 * 无需在此处手动调用 addLayer。
 */
@Mod.EventBusSubscriber(modid = "cnpccurios", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents {

    // 后续可在此处添加客户端特定的饰品渲染逻辑
    // 例如：处理CNPC NPC特有的饰品渲染偏移等
}
