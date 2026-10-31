package cc.sighs.spatialloop.world;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * 把原版的 {@code PerlinNoise}/{@code NormalNoise} 采样换成「在 x/z 上严格周期」的等价采样。
 *
 * <p>原版噪声的输入坐标是绝对坐标乘以倍频系数；只要把晶格索引按周期取模，就能得到同一个噪声场在
 * 有限区域里的周期副本。这里不读原版 {@code ImprovedNoise} 的排列表（它只有 256 项，晶格数不是 256 的
 * 整数倍时接缝会断），而是用「晶格坐标 + 该层的 {@code xo/yo/zo}」重新做梯度哈希。
 *
 * <p>周期以<b>输入单位</b>给出，因为同一个 {@code NormalNoise} 会被不同节点用不同缩放调用：
 * {@code DensityFunctions.Noise} 传入 {@code blockX * xzScale}，{@code ShiftNoise} 传入
 * {@code blockX * 0.25}，{@code SurfaceSystem} 有 {@code x * 1.28} 之类。调用方通过
 * {@link LoopNoisePeriod} 告诉 {@code NormalNoise#getValue} 本次的输入周期 {@code Q}。
 */
public final class LoopNoise {
    /** 原版 {@code NormalNoise#getValue} 给第二个 Perlin 噪声的输入乘数。 */
    public static final double SECOND_INPUT_FACTOR = 1.0181268882175227;

    /** 与 {@code SimplexNoise.GRADIENT} 相同的 16 个梯度（那是包内可见的，不能直接引用）。 */
    private static final int[][] GRADIENT = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };

    /**
     * {@code PerlinNoise} 每层八度的起始振幅系数 {@code 2^(n-1)/(2^n-1)}。
     * 原版在构造时算一次；这里按八度数预计算成表，避免每次采样都调用 {@code Math.pow}。
     */
    private static final double[] LAYER_VALUE_FACTOR = new double[33];

    static {
        for (int levels = 1; levels < LAYER_VALUE_FACTOR.length; levels++) {
            LAYER_VALUE_FACTOR[levels] = Math.pow(2.0, levels - 1) / (Math.pow(2.0, levels) - 1.0);
        }
    }

    private LoopNoise() {
    }

    /**
     * 等价于 {@code NormalNoise#getValue}，但 x/z 以 {@code period} 为周期（输入单位）。
     *
     * @param period 本次调用输入坐标的周期；必须大于 0
     */
    public static double sampleNormal(
            ImprovedNoise[] firstLevels,
            ImprovedNoise[] secondLevels,
            DoubleList amplitudes,
            int firstOctave,
            double valueFactor,
            double u,
            double y,
            double w,
            double period
    ) {
        double first = sampleLayer(firstLevels, amplitudes, firstOctave, 1.0, u, y, w, period);
        double second = sampleLayer(secondLevels, amplitudes, firstOctave, SECOND_INPUT_FACTOR, u, y, w, period);
        return (first + second) * valueFactor;
    }

    /**
     * 一个 {@code PerlinNoise} 的值：每层八度按 {@code cells = round(period * f)} 取模。
     *
     * @param frequencyMultiplier 第二个 Perlin 用 {@link #SECOND_INPUT_FACTOR}，第一个用 1
     */
    public static double sampleLayer(
            ImprovedNoise[] levels,
            DoubleList amplitudes,
            int firstOctave,
            double frequencyMultiplier,
            double u,
            double y,
            double w,
            double period
    ) {
        int count = levels.length;
        double value = 0.0;
        // 与 PerlinNoise 一致：2^(n-1) / (2^n - 1) 起步，逐层减半。
        double valueFactor = count > 0 && count < LAYER_VALUE_FACTOR.length
                ? LAYER_VALUE_FACTOR[count]
                : Math.pow(2.0, count - 1) / (Math.pow(2.0, count) - 1.0);
        for (int i = 0; i < count; i++) {
            double amplitude = i < amplitudes.size() ? amplitudes.getDouble(i) : 0.0;
            ImprovedNoise level = levels[i];
            if (level != null && amplitude != 0.0) {
                double frequency = Math.scalb(1.0, firstOctave + i) * frequencyMultiplier;
                int cells = Math.max(1, (int) Math.rint(period * frequency));
                value += amplitude * valueFactor * sampleOctave(level, u, y, w, cells, frequency, period);
            }
            valueFactor *= 0.5;
        }
        return value;
    }

    /**
     * 单个八度。x/z 的晶格索引按 {@code cells} 取模（取模后的坐标再取 +1 角点），y 不取模。
     */
    static double sampleOctave(
            ImprovedNoise level,
            double u,
            double y,
            double w,
            int cells,
            double frequency,
            double period
    ) {
        // 采样坐标：一个周期正好跨 cells 个晶格。
        double x = u * cells / period + level.xo;
        double yy = y * frequency + level.yo;
        double z = w * cells / period + level.zo;

        int ix = Mth.floor(x);
        int iy = Mth.floor(yy);
        int iz = Mth.floor(z);
        double fx = x - ix;
        double fy = yy - iy;
        double fz = z - iz;

        int x0 = Math.floorMod(ix, cells);
        int x1 = Math.floorMod(ix + 1, cells);
        int z0 = Math.floorMod(iz, cells);
        int z1 = Math.floorMod(iz + 1, cells);
        int y0 = iy;
        int y1 = iy + 1;

        long salt = salt(level);
        double d0 = gradDot(salt, x0, y0, z0, fx, fy, fz);
        double d1 = gradDot(salt, x1, y0, z0, fx - 1.0, fy, fz);
        double d2 = gradDot(salt, x0, y1, z0, fx, fy - 1.0, fz);
        double d3 = gradDot(salt, x1, y1, z0, fx - 1.0, fy - 1.0, fz);
        double d4 = gradDot(salt, x0, y0, z1, fx, fy, fz - 1.0);
        double d5 = gradDot(salt, x1, y0, z1, fx - 1.0, fy, fz - 1.0);
        double d6 = gradDot(salt, x0, y1, z1, fx, fy - 1.0, fz - 1.0);
        double d7 = gradDot(salt, x1, y1, z1, fx - 1.0, fy - 1.0, fz - 1.0);

        double sx = Mth.smoothstep(fx);
        double sy = Mth.smoothstep(fy);
        double sz = Mth.smoothstep(fz);
        return Mth.lerp3(sx, sy, sz, d0, d1, d2, d3, d4, d5, d6, d7);
    }

    private static long salt(ImprovedNoise level) {
        // 游戏内由 ImprovedNoiseSaltMixin 缓存；单元测试（无 mixin 变换）退回逐次计算。
        // ImprovedNoise 是 final 且编译期不实现该接口，所以先退到 Object 再判断。
        Object raw = level;
        if (raw instanceof LoopNoiseSalt cached) {
            return cached.spatialloop$salt();
        }
        return hashSalt(level.xo, level.yo, level.zo);
    }

    /** 由 {@code xo/yo/zo} 派生该层噪声的梯度盐；mixin 用它做一次性缓存。 */
    public static long hashSalt(double xo, double yo, double zo) {
        long hash = mix64(Double.doubleToLongBits(xo));
        hash = mix64(hash ^ Double.doubleToLongBits(yo));
        return mix64(hash ^ Double.doubleToLongBits(zo));
    }

    private static double gradDot(long salt, int x, int y, int z, double dx, double dy, double dz) {
        long hash = salt
                ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) y * 0xC2B2AE3D27D4EB4FL
                ^ (long) z * 0x165667B19E3779F9L;
        int[] gradient = GRADIENT[(int) (mix64(hash) >>> 60) & 15];
        return gradient[0] * dx + gradient[1] * dy + gradient[2] * dz;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 33)) * 0xFF51AFD7ED558CCDL;
        value = (value ^ (value >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return value ^ (value >>> 33);
    }
}
