package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.SpatialLoop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 登录/换维度时把当前维度的循环区块半径推给客户端。
 *
 * <p>1.20.1 还没有原版的 {@code CustomPacketPayload} 体系，Fabric 用
 * {@link ResourceLocation} 作为通道名、{@link FriendlyByteBuf} 作为载荷；
 * 这里保留一个 record 承载半径并负责读写，避免通道名和编码散落在收发两端。
 *
 * <p>{@code ClientLevel} 不携带 {@code ChunkGenerator}，客户端无法自己知道存档里的循环半径；
 * 自适应的球面弯折需要这个值（半径 0 表示该维度没有循环）。
 */
public record LoopRadiusPayload(int radius) {
    public static final ResourceLocation CHANNEL =
            new ResourceLocation(SpatialLoop.MOD_ID, "loop_radius");

    public static LoopRadiusPayload read(FriendlyByteBuf buf) {
        return new LoopRadiusPayload(buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.radius);
    }
}
