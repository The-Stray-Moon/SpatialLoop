package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.SpatialLoop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Forge 1.18.2 的半径载荷：还没有 CustomPacketPayload（1.20.2+），使用通道 + 手写编解码。 */
public record LoopRadiusPayload(int radius) {
    public static final ResourceLocation CHANNEL = new ResourceLocation(SpatialLoop.MOD_ID, "loop_radius");

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.radius);
    }

    public static LoopRadiusPayload decode(FriendlyByteBuf buffer) {
        return new LoopRadiusPayload(buffer.readVarInt());
    }
}
