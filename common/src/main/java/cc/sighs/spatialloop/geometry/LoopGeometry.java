package cc.sighs.spatialloop.geometry;

/**
 * 「空间循环」的几何定义。
 *
 * <p>半径以<b>区块</b>计。半径 {@code r} 表示循环区域覆盖区块坐标
 * {@code [-r, r)}，也就是一个 {@code 2r × 2r} 区块（{@code 32r × 32r} 方块）的方形拼图。
 * 区域东边缘与西边缘、南边缘与北边缘完全相同，因此在区域外按周期取模得到的就是同一块空间。
 *
 * <p>本类不依赖 Minecraft，方便在 common 里单独测试。所有坐标运算都用整数或双精度浮点，
 * 环绕的结果精确可复现（模数是 16 的整数倍，所以方块坐标低 4 位不受环绕影响）。
 */
public final class LoopGeometry {
    public static final int CHUNK_SIZE = 16;
    /** 滑条上限：半径 128 区块 = 256 × 256 区块的循环区域。 */
    public static final int MAX_RADIUS = 128;
    /** 半径为 1/2 时区域只有 2–4 区块宽，放不下「视距严格小于半径」，因此启用循环时的最小值是 3。 */
    public static final int MIN_ACTIVE_RADIUS = 3;

    private LoopGeometry() {
    }

    /** 0 表示关闭；其余值收敛到 {@code [MIN_ACTIVE_RADIUS, MAX_RADIUS]}。 */
    public static int sanitizeRadius(int radius) {
        if (radius <= 0) {
            return 0;
        }
        return Math.min(MAX_RADIUS, Math.max(MIN_ACTIVE_RADIUS, radius));
    }

    public static boolean isEnabled(int radius) {
        return radius > 0;
    }

    public static int periodChunks(int radius) {
        return 2 * radius;
    }

    public static int periodBlocks(int radius) {
        return periodChunks(radius) * CHUNK_SIZE;
    }

    public static int minChunk(int radius) {
        return -radius;
    }

    public static int maxChunkInclusive(int radius) {
        return radius - 1;
    }

    public static int minBlock(int radius) {
        return -radius * CHUNK_SIZE;
    }

    /** 区域的方块坐标上界（不含）。 */
    public static int maxBlockExclusive(int radius) {
        return radius * CHUNK_SIZE;
    }

    /** 把任意区块坐标折回 {@code [-r, r)}。 */
    public static int wrapChunk(int chunk, int radius) {
        int period = periodChunks(radius);
        return Math.floorMod(chunk + radius, period) - radius;
    }

    /** 把任意方块坐标折回 {@code [-16r, 16r)}。 */
    public static int wrapBlock(int block, int radius) {
        int period = periodBlocks(radius);
        return Math.floorMod(block + radius * CHUNK_SIZE, period) - radius * CHUNK_SIZE;
    }

    /**
     * 强制视距：必须严格小于半径，否则玩家能同时看到接缝两侧的同一处地形。
     *
     * @return 允许的最大视距（区块）
     */
    public static int maxViewDistance(int radius) {
        return Math.max(1, radius - 1);
    }

    /** 以 {@code period} 为周期把方块坐标映射到 {@code [0, 1)}，供周期噪声采样。 */
    public static double unit(int block, int period) {
        return (double) Math.floorMod(block, period) / (double) period;
    }
}
