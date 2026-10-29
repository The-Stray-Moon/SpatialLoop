package cc.sighs.spatialloop.world;

/**
 * 视距/模拟距离上限：由原版区块票据的「持有半径」与循环周期共同决定，不改变存档中的周期。
 *
 * <p>推导（Minecraft 1.21.1）：玩家加载票据的等级是
 * {@code ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING) == 31}，而
 * {@code ChunkLevel.MAX_LEVEL == 33 + FULL 生成依赖半径}。票据等级每走一格区块 +1，
 * 只要等级不超过 {@code MAX_LEVEL}，holder 就仍然存在并会被生成，所以实际持有的方形范围
 * 比「票据方形」向外多出 {@code MAX_LEVEL - 31 = GENERATION_RADIUS + 2} 格。
 *
 * <p>同余互斥要求任意两个被持有（可能被生成/保存）的区块不能相差一个周期：
 * {@code 2 * (viewDistance + halo) < 2 * radius}，即
 * {@code viewDistance <= radius - halo - 1 = radius - 14}。
 * 这个上限不能靠改客户端视距绕过：超出的 holder 一旦同时存在，就会重新出现两份可写副本。
 */
public final class LoopLoadingLimits {
    // Minecraft 1.21.1: ChunkPyramid.GENERATION_PYRAMID.getStepTo(FULL)
    //     .getAccumulatedRadiusOf(EMPTY) == 11.
    // Keep this value independent of registry bootstrap so the safety arithmetic remains unit-testable;
    // LoopLoadingLimitsTest pins it against ChunkLevel at runtime.
    private static final int GENERATION_RADIUS = 11;

    /** {@code MAX_LEVEL(33 + GENERATION_RADIUS) - PLAYER_TICKET_LEVEL(31)}。 */
    private static final int LOADED_HALO_RADIUS = GENERATION_RADIUS + 2;

    private LoopLoadingLimits() {
    }

    /** 保证至少能给出原版最小视距 2 的最小半径。 */
    public static int minimumRadius() {
        return LOADED_HALO_RADIUS + 3;
    }

    /** 新建世界的默认半径：视距 18 区块，明显大于原版默认的 10。 */
    public static int defaultRadius() {
        return 32;
    }

    public static void validateRadius(int radius) {
        if (radius != 0 && radius < minimumRadius()) {
            throw new IllegalArgumentException("SpatialLoop radius " + radius
                    + " is unsafe: the minimum supported radius is " + minimumRadius()
                    + " chunks (generation dependency radius " + GENERATION_RADIUS + "). "
                    + "This world's saved period cannot be changed automatically. "
                    + "Use a compatible version to open this world, or create a new world with a supported radius.");
        }
    }

    /**
     * 循环维度允许的最大视距与模拟距离（区块）。
     *
     * <p>不能再大：视距每加 1，被持有的方形每边就加 1；两边之和达到一个周期时，
     * 同一份 canonical 数据会同时出现两个可写 holder。
     */
    public static int maxDistance(int radius) {
        validateRadius(radius);
        return radius == 0 ? Integer.MAX_VALUE : Math.max(2, radius - LOADED_HALO_RADIUS - 1);
    }

    /** 票据方形之外仍被原版保留/生成的区块圈数，用于诊断与测试。 */
    public static int loadedHaloRadius() {
        return LOADED_HALO_RADIUS;
    }

    /** 固定目标版本的原版 FULL 生成依赖半径，用于和运行时 {@code ChunkLevel} 常量比对。 */
    public static int pinnedGenerationRadius() {
        return GENERATION_RADIUS;
    }
}
