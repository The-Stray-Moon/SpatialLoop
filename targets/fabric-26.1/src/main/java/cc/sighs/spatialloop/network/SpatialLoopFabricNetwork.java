package cc.sighs.spatialloop.network;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Fabric 26.1 的循环半径同步；载荷类型与 NeoForge 版本相同，只换注册/发送 API。 */
public final class SpatialLoopFabricNetwork {
    private SpatialLoopFabricNetwork() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(LoopRadiusPayload.TYPE, LoopRadiusPayload.STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> LoopRadiusSync.send(handler.player)
        );
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> LoopRadiusSync.send(player)
        );
    }

    public static void sendLoopRadius(ServerPlayer player, int radius) {
        ServerPlayNetworking.send(player, new LoopRadiusPayload(radius));
    }
}
