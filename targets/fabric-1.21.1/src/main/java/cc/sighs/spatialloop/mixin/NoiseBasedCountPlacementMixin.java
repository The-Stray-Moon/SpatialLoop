package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopFeatureNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.placement.NoiseBasedCountPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 海带/海草等的数量来自 {@code Biome.BIOME_INFO_NOISE}，按折回后的方块坐标采样才周期。 */
@Mixin(NoiseBasedCountPlacement.class)
public abstract class NoiseBasedCountPlacementMixin {
    @Redirect(
            method = "count",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getX()I")
    )
    private int spatialloop$periodicX(BlockPos pos) {
        return LoopFeatureNoise.wrap(pos.getX());
    }

    @Redirect(
            method = "count",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getZ()I")
    )
    private int spatialloop$periodicZ(BlockPos pos) {
        return LoopFeatureNoise.wrap(pos.getZ());
    }
}
