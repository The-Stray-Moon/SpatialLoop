package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoisePeriod;
import cc.sighs.spatialloop.world.LoopNoisePeriodAware;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@code DensityFunctions.WeirdScaledSampler}（意大利面洞穴）：输入是 {@code blockX / d0}，
 * {@code d0} 由 {@code rarityValueMapper.mapper.get(input)} 得到，所以输入周期是 {@code 方块周期 / d0}。
 *
 * <p>字节码里 {@code mapper.get(input)} 紧挨着噪声调用，先在第一个 Redirect 里把 d0 暂存到线程内，
 * 再由第二个 Redirect 取出算周期——这样不必给包内可见的 {@code RarityValueMapper} 类型命名。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler")
public abstract class DensityFunctionsWeirdScaledSamplerMixin {
    @Redirect(
            method = "transform",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/doubles/Double2DoubleFunction;get(D)D"
            )
    )
    private double spatialloop$captureRarity(Double2DoubleFunction mapper, double input) {
        double rarity = mapper.get(input);
        LoopNoisePeriod.setRarity(rarity);
        return rarity;
    }

    @Redirect(
            method = "transform",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/DensityFunction$NoiseHolder;getValue(DDD)D"
            )
    )
    private double spatialloop$periodicNoise(DensityFunction.NoiseHolder holder, double x, double y, double z) {
        double rarity = LoopNoisePeriod.takeRarity();
        int period = LoopNoisePeriodAware.periodOf(holder.noise());
        if (period <= 0 || !(rarity > 0.0)) {
            return holder.getValue(x, y, z);
        }
        LoopNoisePeriod.push(period / rarity, 1.0);
        try {
            return holder.getValue(x, y, z);
        } finally {
            LoopNoisePeriod.pop();
        }
    }
}
