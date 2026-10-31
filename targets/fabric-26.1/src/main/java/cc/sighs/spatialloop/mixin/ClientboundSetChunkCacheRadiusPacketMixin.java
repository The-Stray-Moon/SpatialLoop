package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.SpatialLoopViewDistance;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 运行中调整视距时（单人设置 / 局域网 / {@code /viewdistance}）同样收窄到循环半径以下。 */
@Mixin(ClientboundSetChunkCacheRadiusPacket.class)
public abstract class ClientboundSetChunkCacheRadiusPacketMixin {
    @ModifyVariable(method = "<init>(I)V", at = @At("HEAD"), argsOnly = true, index = 1)
    private static int spatialloop$clampChunkCacheRadius(int radius) {
        return SpatialLoopViewDistance.clamp(radius);
    }
}
