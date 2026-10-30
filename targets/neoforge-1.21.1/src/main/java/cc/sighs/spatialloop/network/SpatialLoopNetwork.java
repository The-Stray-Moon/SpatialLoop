package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.client.SpatialLoopCurve;
import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 循环半径的服务端→客户端同步：客户端只在渲染时用，不参与世界数据。
 *
 * <p>登录和换维度时各发一次；服务端只为循环维度发正数，下界/末地发 0。
 */
public final class SpatialLoopNetwork {
    private SpatialLoopNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(SpatialLoopNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(SpatialLoopNetwork::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(SpatialLoopNetwork::onChangedDimension);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                LoopRadiusPayload.TYPE,
                LoopRadiusPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // 专服不会执行 playToClient 处理器；保险起见再判断一次 dist。
                    if (FMLEnvironment.dist == Dist.CLIENT) {
                        SpatialLoopCurve.setServerLoopRadius(payload.radius());
                    }
                })
        );
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    private static void send(ServerPlayer player) {
        int radius = LoopLevel.radiusOf(player.serverLevel());
        PacketDistributor.sendToPlayer(player, new LoopRadiusPayload(radius));
    }
}
