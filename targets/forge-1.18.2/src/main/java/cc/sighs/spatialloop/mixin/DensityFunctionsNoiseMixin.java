package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoisePeriod;
import cc.sighs.spatialloop.world.LoopNoisePeriodAware;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code DensityFunctions.Noise}：输入是 {@code blockX * xzScale}，所以输入周期是
 * {@code 周期方块数 × xzScale}。y 输入已经是 {@code blockY * yScale}，保持原版。
 *
 * <p>1.18.2 的这个记录直接持有 {@code NormalNoise}，没有 {@code NoiseHolder}。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Noise")
public abstract class DensityFunctionsNoiseMixin {
    @Shadow
    @Final
    private double xzScale;

    @Redirect(
            method = "compute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"
            )
    )
    private double spatialloop$periodicNoise(NormalNoise noise, double x, double y, double z) {
        int period = LoopNoisePeriodAware.periodOf(noise);
        if (period <= 0) {
            return noise.getValue(x, y, z);
        }
        LoopNoisePeriod.push(period * this.xzScale, 1.0);
        try {
            return noise.getValue(x, y, z);
        } finally {
            LoopNoisePeriod.pop();
        }
    }
}
