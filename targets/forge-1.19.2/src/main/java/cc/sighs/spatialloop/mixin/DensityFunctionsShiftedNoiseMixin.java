package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoisePeriod;
import cc.sighs.spatialloop.world.LoopNoisePeriodAware;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code DensityFunctions.ShiftedNoise}：{@code blockX * xzScale + shiftX}。shift 本身按同样周期折回，
 * 所以整体对 {@code blockX} 的周期仍是 {@code 周期方块数 × xzScale}。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise")
public abstract class DensityFunctionsShiftedNoiseMixin {
    @Shadow
    @Final
    private double xzScale;

    @Redirect(
            method = "compute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/DensityFunction$NoiseHolder;getValue(DDD)D"
            )
    )
    private double spatialloop$periodicNoise(DensityFunction.NoiseHolder holder, double x, double y, double z) {
        int period = LoopNoisePeriodAware.periodOf(holder.noise());
        if (period <= 0) {
            return holder.getValue(x, y, z);
        }
        LoopNoisePeriod.push(period * this.xzScale, 1.0);
        try {
            return holder.getValue(x, y, z);
        } finally {
            LoopNoisePeriod.pop();
        }
    }
}
