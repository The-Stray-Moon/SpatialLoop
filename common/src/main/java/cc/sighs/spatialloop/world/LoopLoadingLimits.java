package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.spi.LoopGenerationProfile;
import cc.sighs.spatialloop.spi.SpatialLoopServices;

/**
 * 视距/模拟距离上限：由原版区块票据的「持有半径」与循环周期共同决定，不改变存档中的周期。
 *
 * <p>推导（通用形式）：玩家加载票据会持有到 {@code ChunkLevel.MAX_LEVEL}，而玩家票据等级是
 * {@code ENTITY_TICKING}，两者之差就是票据方形之外仍会被保留/生成的圈数（halo）。同余互斥要求
 * 任意两个被持有（可能被生成/保存）的区块不能相差一个周期：
 * {@code 2 * (viewDistance + halo) < 2 * radius}，即
 * {@code viewDistance <= radius - halo - 1}。
 *
 * <p>{@code generationRadius}/{@code halo} 是版本差异，由 target 通过
 * {@link LoopGenerationProfile} 提供；本类只保留公式，不引用任何版本常量。
 * 这个上限不能靠改客户端视距绕过：超出的 holder 一旦同时存在，就会重新出现两份可写副本。
 */
public final class LoopLoadingLimits {
    /** 原版允许的最小视距。 */
    public static final int MINIMUM_VIEW_DISTANCE = 2;

    private LoopLoadingLimits() {
    }

    /** 保证至少能给出原版最小视距 2 的最小半径。 */
    public static int minimumRadius() {
        return minimumRadius(profile());
    }

    public static int minimumRadius(LoopGenerationProfile profile) {
        return profile.loadedHaloRadius() + MINIMUM_VIEW_DISTANCE + 1;
    }

    /** 新建世界的默认半径：视距 18 区块，明显大于原版默认的 10。 */
    public static int defaultRadius() {
        return 32;
    }

    public static void validateRadius(int radius) {
        validateRadius(radius, profile());
    }

    public static void validateRadius(int radius, LoopGenerationProfile profile) {
        if (radius != 0 && radius < minimumRadius(profile)) {
            throw new IllegalArgumentException("SpatialLoop radius " + radius
                    + " is unsafe: the minimum supported radius is " + minimumRadius(profile)
                    + " chunks (generation dependency radius " + profile.generationRadius() + "). "
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
        return maxDistance(radius, profile());
    }

    public static int maxDistance(int radius, LoopGenerationProfile profile) {
        validateRadius(radius, profile);
        return radius == 0
                ? Integer.MAX_VALUE
                : Math.max(MINIMUM_VIEW_DISTANCE, radius - profile.loadedHaloRadius() - 1);
    }

    /** 票据方形之外仍被原版保留/生成的区块圈数，用于诊断与测试。 */
    public static int loadedHaloRadius() {
        return profile().loadedHaloRadius();
    }

    public static int loadedHaloRadius(LoopGenerationProfile profile) {
        return profile.loadedHaloRadius();
    }

    /** 固定目标版本的原版 FULL 生成依赖半径，用于和运行时 {@code ChunkLevel} 常量比对。 */
    public static int pinnedGenerationRadius() {
        return profile().generationRadius();
    }

    public static int pinnedGenerationRadius(LoopGenerationProfile profile) {
        return profile.generationRadius();
    }

    private static LoopGenerationProfile profile() {
        return SpatialLoopServices.platform().generationProfile();
    }
}
