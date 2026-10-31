package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.spi.SpatialLoopServices;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric 1.21.1 的循环半径同步。载荷类型与 NeoForge 完全相同（都是原版
 * {@code CustomPacketPayload}），只是注册/发送 API 换成 Fabric Networking。
 */
public final class SpatialLoopFabricNetwork {
    private SpatialLoopFabricNetwork() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(LoopRadiusPayload.TYPE, LoopRadiusPayload.STREAM_CODEC);
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> LoopRadiusSync.send(handler.player)
        );
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> LoopRadiusSync.send(player)
        );
    }

    public static void sendLoopRadius(ServerPlayer player, int radius) {
        ServerPlayNetworking.send(player, new LoopRadiusPayload(radius));
    }
}
