package cc.sighs.spatialloop.network;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fabric 1.20.1 的循环半径同步。1.20.1 没有 {@code CustomPacketPayload}/{@code PayloadTypeRegistry}，
 * 改用 Identifier 通道（{@link LoopRadiusPayload#CHANNEL}）加 {@link FriendlyByteBuf} 的旧 API；
 * 「什么时候发、发什么值」仍由 common 的 {@code LoopRadiusSync} 决定。
 */
public final class SpatialLoopFabricNetwork {
    private SpatialLoopFabricNetwork() {
    }

    public static void registerPayloads() {
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> LoopRadiusSync.send(handler.player)
        );
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> LoopRadiusSync.send(player)
        );
    }

    public static void sendLoopRadius(ServerPlayer player, int radius) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        new LoopRadiusPayload(radius).write(buf);
        ServerPlayNetworking.send(player, LoopRadiusPayload.CHANNEL, buf);
    }
}
