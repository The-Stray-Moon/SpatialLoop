package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.SpatialLoopViewDistance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 登录包里的 {@code chunkRadius} 决定客户端的渲染/雾距离上限
 * （{@code ClientPacketListener} 直接把它交给 {@code Options#setServerRenderDistance}）。
 * 把它压到循环半径以下，玩家在接缝附近就不可能同时看到同一处地形的两份。
 *
 * <p>1.18.2 的记录构造器参数列表与 1.20.1 不同（没有 {@code CommonPlayerSpawnInfo}），而且
 * Mixin 0.8.5 对构造器注入有限制；这里直接改写 <b>发包</b> 的第三个 {@code writeVarInt}
 * （{@code maxPlayers}、{@code chunkRadius}、{@code simulationDistance} 中的第二个），
 * 让发到客户端的就是钳制后的值，多人客户端与单人一致。
 */
@Mixin(ClientboundLoginPacket.class)
public abstract class ClientboundLoginPacketMixin {
    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;writeVarInt(I)Lnet/minecraft/network/FriendlyByteBuf;",
                    ordinal = 1
            )
    )
    private FriendlyByteBuf spatialloop$clampChunkRadius(FriendlyByteBuf buffer, int chunkRadius) {
        return buffer.writeVarInt(SpatialLoopViewDistance.clamp(chunkRadius));
    }
}
