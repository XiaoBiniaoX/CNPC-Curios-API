package top.bincnpc.cnpccurios.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import noppes.npcs.entity.EntityNPCInterface;
import top.bincnpc.cnpccurios.CNPCcurios;

import java.util.function.Supplier;

/**
 * 网络包：通知客户端刷新NPC的Curios饰品数据。
 */
public class SyncCuriosPacket {

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(CNPCcurios.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private final int entityId;

    public SyncCuriosPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void register() {
        CHANNEL.messageBuilder(SyncCuriosPacket.class, 0)
                .encoder(SyncCuriosPacket::encode)
                .decoder(SyncCuriosPacket::decode)
                .consumerMainThread(SyncCuriosPacket::handle)
                .add();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
    }

    public static SyncCuriosPacket decode(FriendlyByteBuf buf) {
        return new SyncCuriosPacket(buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            CNPCcurios.LOGGER.debug("收到NPC {} 饰品同步包", entityId);
        });
        ctx.get().setPacketHandled(true);
    }

    public static void sendToTracking(LivingEntity entity) {
        if (!(entity instanceof EntityNPCInterface)) return;
        if (entity.level().isClientSide()) return;

        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                new SyncCuriosPacket(entity.getId()));
    }
}
