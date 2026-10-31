package cc.sighs.spatialloop.world;

import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * {@code DensityFunctions.ShiftNoise} 的周期版本（1.18.2 版）。
 *
 * <p>1.18.2 的 shift 噪声直接持有 {@link NormalNoise}，没有 1.20.1 的 {@code NoiseHolder} 包装，
 * 所以这里直接接收噪声实例。输入是方块坐标的 0.25 倍，输入周期是方块周期的四分之一。
 */
public final class LoopShiftNoise {
    private LoopShiftNoise() {
    }

    public static double sample(NormalNoise noise, double x, double y, double z) {
        if (noise == null) {
            return 0.0;
        }
        int period = LoopNoisePeriodAware.periodOf(noise);
        if (period <= 0) {
            return noise.getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        }
        LoopNoisePeriod.push(period * 0.25, 1.0);
        try {
            return noise.getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        } finally {
            LoopNoisePeriod.pop();
        }
    }
}
