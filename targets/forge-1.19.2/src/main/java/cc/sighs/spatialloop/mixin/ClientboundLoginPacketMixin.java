package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.SpatialLoopViewDistance;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 登录包里的 {@code chunkRadius} 决定客户端的渲染/雾距离上限（{@code Options#getEffectiveRenderDistance}）。
 * 把它压到循环半径以下，玩家在接缝附近就不可能同时看到同一处地形的两份。
 *
 * <p>1.19.2 的 {@link ClientboundLoginPacket} 是 record，没有 {@code CommonPlayerSpawnInfo}；
 * 规范构造器的参数（含 long 占两个槽位）依次是：
 * {@code 1 playerId, 2 hardcore, 3 gameType, 4 previousGameType, 5 levels, 6 registryHolder,
 * 7 dimensionType, 8 dimension, 9-10 seed, 11 maxPlayers, 12 chunkRadius, 13 simulationDistance, ...}，
 * 因此 {@code chunkRadius} 在实例方法的局部变量表里是 12。
 */
@Mixin(ClientboundLoginPacket.class)
public abstract class ClientboundLoginPacketMixin {
    @ModifyVariable(
            method = "<init>(IZLnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;Ljava/util/Set;Lnet/minecraft/core/RegistryAccess$Frozen;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;JIIIZZZZLjava/util/Optional;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 12
    )
    private static int spatialloop$clampChunkRadius(int chunkRadius) {
        return SpatialLoopViewDistance.clamp(chunkRadius);
    }
}
