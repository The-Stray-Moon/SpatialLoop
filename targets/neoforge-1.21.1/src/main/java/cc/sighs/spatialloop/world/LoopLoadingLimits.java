package cc.sighs.spatialloop.world;

/** 为固定目标版本的原版生成依赖圈及所有权交接留出空间，不改变存档中的周期。 */
public final class LoopLoadingLimits {
    // Minecraft 1.21.1: ChunkPyramid.GENERATION_PYRAMID FULL -> EMPTY accumulated radius.
    // Keep this value independent of registry bootstrap so the safety arithmetic remains unit-testable.
    private static final int GENERATION_RADIUS = 11;

    private LoopLoadingLimits() {
    }

    public static int minimumRadius() {
        return GENERATION_RADIUS + 5;
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

    /** 循环关闭时不限制距离；启用时先拒绝不安全的存档半径。 */
    public static int maxDistance(int radius) {
        validateRadius(radius);
        return radius == 0 ? Integer.MAX_VALUE : radius - GENERATION_RADIUS - 3;
    }
}
