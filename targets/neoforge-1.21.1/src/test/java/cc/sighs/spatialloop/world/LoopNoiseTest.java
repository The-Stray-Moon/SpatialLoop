package cc.sighs.spatialloop.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.junit.jupiter.api.Test;

/**
 * 周期噪声的数学不变量（不经过 mixin，直接调 {@link LoopNoise}）。
 *
 * <p>这层如果错了，游戏里会表现为副本区块不一致或接缝断崖，但单元测试能先把它钉死。
 */
class LoopNoiseTest {
    private static final int[] FIRST_OCTAVES = {-8, -7, -5, -2, 0};
    private static final double[] PERIODS = {96.0, 1024.0, 4096.0};

    private static ImprovedNoise[] levels(int count, long seed) {
        RandomSource random = new LegacyRandomSource(seed);
        ImprovedNoise[] levels = new ImprovedNoise[count];
        for (int i = 0; i < count; i++) {
            levels[i] = new ImprovedNoise(random);
        }
        return levels;
    }

    private static DoubleArrayList amplitudes(int count) {
        DoubleArrayList amplitudes = new DoubleArrayList();
        for (int i = 0; i < count; i++) {
            amplitudes.add(1.0);
        }
        return amplitudes;
    }

    @Test
    void layerRepeatsExactlyAtTheInputPeriod() {
        for (double period : PERIODS) {
            for (int firstOctave : FIRST_OCTAVES) {
                ImprovedNoise[] levels = levels(4, 42L + firstOctave);
                DoubleArrayList amplitudes = amplitudes(4);
                // 第二个 Perlin 的频率乘了 1.0181268882175227，周期必须仍然严格等于 Q。
                for (double multiplier : new double[]{1.0, LoopNoise.SECOND_INPUT_FACTOR}) {
                    for (double u : new double[]{0.0, 1.25, -450.5, 511.75, -1023.5}) {
                        for (double y : new double[]{-64.0, 0.0, 300.5}) {
                            double base = LoopNoise.sampleLayer(
                                    levels, amplitudes, firstOctave, multiplier, u, y, 33.5, period);
                            double shifted = LoopNoise.sampleLayer(
                                    levels, amplitudes, firstOctave, multiplier, u + period, y, 33.5, period);
                            assertEquals(base, shifted, 1e-6,
                                    "period " + period + " octave " + firstOctave + " u " + u);
                        }
                    }
                }
            }
        }
    }

    @Test
    void seamIsAsContinuousAsAnyOtherNeighbourPair() {
        double step = 0.25;
        for (double period : PERIODS) {
            for (int firstOctave : FIRST_OCTAVES) {
                ImprovedNoise[] levels = levels(4, 7L);
                DoubleArrayList amplitudes = amplitudes(4);
                double previous = LoopNoise.sampleLayer(
                        levels, amplitudes, firstOctave, 1.0, 0.0, 17.0, 0.0, period);
                double first = previous;
                double maxNeighbour = 0.0;
                for (double u = step; u < period; u += step) {
                    double current = LoopNoise.sampleLayer(
                            levels, amplitudes, firstOctave, 1.0, u, 17.0, 0.0, period);
                    maxNeighbour = Math.max(maxNeighbour, Math.abs(current - previous));
                    previous = current;
                }
                // 从 Q-step 走到 0（接缝）：差值不能超过普通相邻采样的若干倍。
                double seam = Math.abs(first - previous);
                assertTrue(seam <= 4.0 * maxNeighbour + 1e-6,
                        "period " + period + " octave " + firstOctave
                                + " seam " + seam + " neighbour " + maxNeighbour);
            }
        }
    }

    @Test
    void valuesVaryAcrossTheLoop() {
        double period = 1024.0;
        for (int firstOctave : FIRST_OCTAVES) {
            ImprovedNoise[] levels = levels(3, 99L);
            DoubleArrayList amplitudes = amplitudes(3);
            double low = LoopNoise.sampleLayer(levels, amplitudes, firstOctave, 1.0, 0.0, 0.0, 0.0, period);
            double high = LoopNoise.sampleLayer(levels, amplitudes, firstOctave, 1.0, period * 0.3, 0.0, 0.0, period);
            assertNotEquals(low, high, "octave " + firstOctave + " is flat");
        }
    }
}
