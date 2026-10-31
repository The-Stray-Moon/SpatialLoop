package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.client.SpatialLoopCurve;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge 1.18.2 的循环半径同步；何时发送/发送什么仍由 common 的 LoopRadiusSync 决定。 */
public final class SpatialLoopForgeNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            LoopRadiusPayload.CHANNEL,
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private SpatialLoopForgeNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(LoopRadiusPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LoopRadiusPayload::encode)
                .decoder(LoopRadiusPayload::decode)
                // 1.18.2 只有 consumer（没有 1.19+ 的 consumerMainThread）：
                // 这里显式把客户端处理排到主线程，并标记包已处理。
                .consumer((payload, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    context.enqueueWork(() -> {
                        if (FMLEnvironment.dist == Dist.CLIENT) {
                            SpatialLoopCurve.setServerLoopRadius(payload.radius());
                        }
                    });
                    context.setPacketHandled(true);
                })
                .add();
    }

    public static void sendLoopRadius(ServerPlayer player, int radius) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new LoopRadiusPayload(radius));
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            LoopRadiusSync.send(player);
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            LoopRadiusSync.send(player);
        }
    }
}
