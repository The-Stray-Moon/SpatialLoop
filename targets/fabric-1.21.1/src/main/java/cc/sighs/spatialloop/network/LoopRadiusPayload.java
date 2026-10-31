package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.SpatialLoop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 登录/换维度时把当前维度的循环区块半径推给客户端。
 *
 * <p>{@code ClientLevel} 不携带 {@code ChunkGenerator}，客户端无法自己知道存档里的循环半径；
 * 自适应的球面弯折需要这个值（半径 0 表示该维度没有循环）。
 */
public record LoopRadiusPayload(int radius) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LoopRadiusPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(SpatialLoop.MOD_ID, "loop_radius")
    );

    public static final StreamCodec<ByteBuf, LoopRadiusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LoopRadiusPayload::radius, LoopRadiusPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
