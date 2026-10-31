package cc.sighs.spatialloop.world;

/** 生成依赖范围的纯几何计算，与 Minecraft 版本无关。 */
public final class LoopDependencyMath {
    private LoopDependencyMath() {
    }

    public static int squareDependencyCount(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Generation dependency radius must not be negative: " + radius);
        }
        if (radius == 0) {
            return 1;
        }
        int side = Math.addExact(Math.multiplyExact(radius, 2), 1);
        return Math.multiplyExact(side, side);
    }
}
