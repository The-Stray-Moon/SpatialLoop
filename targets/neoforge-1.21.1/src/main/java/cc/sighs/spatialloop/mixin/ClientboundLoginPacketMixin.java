package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.SpatialLoopViewDistance;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 登录包里的 {@code chunkRadius} 决定客户端的渲染/雾距离上限（{@code Options#getEffectiveRenderDistance}）。
 * 把它压到循环半径以下，玩家在接缝附近就不可能同时看到同一处地形的两份。
 */
@Mixin(ClientboundLoginPacket.class)
public abstract class ClientboundLoginPacketMixin {
    @ModifyVariable(
            method = "<init>(IZLjava/util/Set;IIIZZZLnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 5
    )
    private static int spatialloop$clampChunkRadius(int chunkRadius) {
        return SpatialLoopViewDistance.clamp(chunkRadius);
    }
}
