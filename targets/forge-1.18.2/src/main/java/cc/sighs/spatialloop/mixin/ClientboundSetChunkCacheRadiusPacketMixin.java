package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.SpatialLoopViewDistance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 运行中调整视距时（单人设置 / 局域网 / {@code /viewdistance}）同样收窄到循环半径以下。
 *
 * <p>与 {@link ClientboundLoginPacketMixin} 一样改写发包值：1.18.2 的 {@code write} 里只有一个
 * {@code writeVarInt}，直接改成钳制后的半径。
 */
@Mixin(ClientboundSetChunkCacheRadiusPacket.class)
public abstract class ClientboundSetChunkCacheRadiusPacketMixin {
    @Redirect(
            method = "write",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/FriendlyByteBuf;writeVarInt(I)Lnet/minecraft/network/FriendlyByteBuf;"
            )
    )
    private FriendlyByteBuf spatialloop$clampChunkCacheRadius(FriendlyByteBuf buffer, int radius) {
        return buffer.writeVarInt(SpatialLoopViewDistance.clamp(radius));
    }
}
