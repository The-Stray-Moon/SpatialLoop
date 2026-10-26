package cc.sighs.spatialloop.noise;

/**
 * 严格周期性的梯度噪声（Perlin 风格）。
 *
 * <p>普通噪声按绝对坐标采样时，{@code f(x)} 与 {@code f(x + P)} 没有关系，接缝处必然断开。
 * 这里把采样坐标归一化到 {@code [0, 1)}，并把晶格索引按每层晶格数取模，于是
 * {@code noise(u)} 与 {@code noise(u + 1)} 在数学上完全相等——不是「近似」，而是同一个晶格点、
 * 同一个哈希、同一个插值结果。这就是地形东拼西、南拼北能严丝合缝的基础。
 *
 * <p>竖直方向（3D 噪声的 y）不做周期化：世界上下是有限的。
 */
public final class PeriodicNoise {
    private static final double SQRT_HALF = 0.7071067811865476;
    private static final double[] GRADIENTS_2D = {
            1, 0, -1, 0, 0, 1, 0, -1, SQRT_HALF, SQRT_HALF, -SQRT_HALF, SQRT_HALF, SQRT_HALF, -SQRT_HALF, -SQRT_HALF, -SQRT_HALF
    };
    private static final double[] GRADIENTS_3D = {
            1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0,
            1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1,
            0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1
    };
    /** 2D Perlin 的理论最大值约为 0.7071。 */
    private static final double NORMALIZE_2D = 1.4142135623730951;
    /** 3D Perlin 的理论最大值约为 0.866。 */
    private static final double NORMALIZE_3D = 1.1547005383792517;

    private final long seed;

    public PeriodicNoise(long seed) {
        this.seed = seed;
    }

    /**
     * 2D 周期噪声。
     *
     * @param u     水平坐标，{@code [0, 1)}
     * @param v     另一水平坐标，{@code [0, 1)}
     * @param cellsU u 方向一个周期内的晶格数（≥ 1）
     * @param cellsV v 方向一个周期内的晶格数（≥ 1）
     * @return 大致落在 {@code [-1, 1]}
     */
    public double noise2(double u, double v, int cellsU, int cellsV) {
        double fu = u * cellsU;
        double fv = v * cellsV;
        int iu = floor(fu);
        int iv = floor(fv);
        double tu = fu - iu;
        double tv = fv - iv;
        int u0 = Math.floorMod(iu, cellsU);
        int v0 = Math.floorMod(iv, cellsV);
        int u1 = Math.floorMod(iu + 1, cellsU);
        int v1 = Math.floorMod(iv + 1, cellsV);

        double su = fade(tu);
        double sv = fade(tv);
        double n00 = gradientDot2(hash2(u0, v0), tu, tv);
        double n10 = gradientDot2(hash2(u1, v0), tu - 1.0, tv);
        double n01 = gradientDot2(hash2(u0, v1), tu, tv - 1.0);
        double n11 = gradientDot2(hash2(u1, v1), tu - 1.0, tv - 1.0);
        double a = lerp(su, n00, n10);
        double b = lerp(su, n01, n11);
        return lerp(sv, a, b) * NORMALIZE_2D;
    }

    /** 2D 分形噪声，逐层晶格数翻倍、振幅乘以 {@code persistence}。 */
    public double fbm2(double u, double v, int cellsU, int cellsV, int octaves, double persistence) {
        double sum = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        int cu = Math.max(1, cellsU);
        int cv = Math.max(1, cellsV);
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * noise2(u, v, cu, cv);
            total += amplitude;
            amplitude *= persistence;
            cu = Math.min(cu * 2, 1 << 20);
            cv = Math.min(cv * 2, 1 << 20);
        }
        return total == 0.0 ? 0.0 : sum / total;
    }

    /**
     * 3D 周期噪声：水平方向周期、竖直方向不周期。
     *
     * @param cellSizeY y 方向一层晶格的高度（方块）
     */
    public double noise3(double u, double v, double y, int cellsU, int cellsV, double cellSizeY) {
        double fu = u * cellsU;
        double fv = v * cellsV;
        double fy = y / cellSizeY;
        int iu = floor(fu);
        int iv = floor(fv);
        int iy = floor(fy);
        double tu = fu - iu;
        double tv = fv - iv;
        double ty = fy - iy;
        int u0 = Math.floorMod(iu, cellsU);
        int v0 = Math.floorMod(iv, cellsV);
        int u1 = Math.floorMod(iu + 1, cellsU);
        int v1 = Math.floorMod(iv + 1, cellsV);

        double su = fade(tu);
        double sv = fade(tv);
        double sy = fade(ty);

        double n000 = gradientDot3(hash3(u0, v0, iy), tu, tv, ty);
        double n100 = gradientDot3(hash3(u1, v0, iy), tu - 1.0, tv, ty);
        double n010 = gradientDot3(hash3(u0, v1, iy), tu, tv - 1.0, ty);
        double n110 = gradientDot3(hash3(u1, v1, iy), tu - 1.0, tv - 1.0, ty);
        double n001 = gradientDot3(hash3(u0, v0, iy + 1), tu, tv, ty - 1.0);
        double n101 = gradientDot3(hash3(u1, v0, iy + 1), tu - 1.0, tv, ty - 1.0);
        double n011 = gradientDot3(hash3(u0, v1, iy + 1), tu, tv - 1.0, ty - 1.0);
        double n111 = gradientDot3(hash3(u1, v1, iy + 1), tu - 1.0, tv - 1.0, ty - 1.0);

        double a = lerp(su, n000, n100);
        double b = lerp(su, n010, n110);
        double c = lerp(su, n001, n101);
        double d = lerp(su, n011, n111);
        double e = lerp(sv, a, b);
        double f = lerp(sv, c, d);
        return lerp(sy, e, f) * NORMALIZE_3D;
    }

    /** 3D 分形噪声。 */
    public double fbm3(double u, double v, double y, int cellsU, int cellsV, double cellSizeY, int octaves, double persistence) {
        double sum = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        int cu = Math.max(1, cellsU);
        int cv = Math.max(1, cellsV);
        double cy = cellSizeY;
        for (int i = 0; i < octaves; i++) {
            sum += amplitude * noise3(u, v, y, cu, cv, cy);
            total += amplitude;
            amplitude *= persistence;
            cu = Math.min(cu * 2, 1 << 20);
            cv = Math.min(cv * 2, 1 << 20);
            cy = Math.max(1.0, cy * 0.5);
        }
        return total == 0.0 ? 0.0 : sum / total;
    }

    private static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double gradientDot2(int hash, double x, double y) {
        int index = (hash & 7) * 2;
        return GRADIENTS_2D[index] * x + GRADIENTS_2D[index + 1] * y;
    }

    private static double gradientDot3(int hash, double x, double y, double z) {
        int index = Math.floorMod(hash, 12) * 3;
        return GRADIENTS_3D[index] * x + GRADIENTS_3D[index + 1] * y + GRADIENTS_3D[index + 2] * z;
    }

    private int hash2(int u, int v) {
        long h = mix(seed ^ 0x9E3779B97F4A7C15L);
        h = mix(h ^ (u * 0x9E3779B97F4A7C15L));
        h = mix(h ^ (v * 0xC2B2AE3D27D4EB4FL));
        return (int) (h >>> 32);
    }

    private int hash3(int u, int v, int y) {
        long h = mix(seed ^ 0xD1B54A32D192ED03L);
        h = mix(h ^ (u * 0x9E3779B97F4A7C15L));
        h = mix(h ^ (v * 0xC2B2AE3D27D4EB4FL));
        h = mix(h ^ (y * 0x165667B19E3779F9L));
        return (int) (h >>> 32);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
