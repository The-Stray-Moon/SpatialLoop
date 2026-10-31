package cc.sighs.spatialloop.world;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code DensityFunctions.ShiftNoise} 的周期版本：{@code offsetNoise.getValue(x*0.25, y*0.25, z*0.25) * 4}。
 *
 * <p>输入是方块坐标的 0.25 倍，所以输入周期是方块周期的四分之一。
 */
public final class LoopShiftNoise {
    private LoopShiftNoise() {
    }

    public static double sample(DensityFunction.NoiseHolder holder, double x, double y, double z) {
        int period = LoopNoisePeriodAware.periodOf(holder.noise());
        if (period <= 0) {
            return holder.getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        }
        LoopNoisePeriod.push(period * 0.25, 1.0);
        try {
            return holder.getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        } finally {
            LoopNoisePeriod.pop();
        }
    }
}
