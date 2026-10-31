package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopCarverSeed;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 雕刻器种子按折回后的区块坐标取。
 *
 * <p>{@code NoiseBasedChunkGenerator#applyCarvers} 会对每个相邻区块调用
 * {@code setLargeFeatureSeed(seed, chunkX, chunkZ)}。洞穴布局相对区块原点，只要种子在副本之间相同，
 * 洞穴就逐块相同；{@link LoopCarverSeed} 只在 {@code applyCarvers} 期间有值，其他调用不受影响。
 */
@Mixin(WorldgenRandom.class)
public abstract class WorldgenRandomMixin {
    @ModifyVariable(method = "setLargeFeatureSeed", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int spatialloop$wrapChunkX(int chunkX) {
        int radius = LoopCarverSeed.current();
        return radius > 0 ? LoopGeometry.wrapChunk(chunkX, radius) : chunkX;
    }

    @ModifyVariable(method = "setLargeFeatureSeed", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int spatialloop$wrapChunkZ(int chunkZ) {
        int radius = LoopCarverSeed.current();
        return radius > 0 ? LoopGeometry.wrapChunk(chunkZ, radius) : chunkZ;
    }
}
