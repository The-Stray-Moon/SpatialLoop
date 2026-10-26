package cc.sighs.spatialloop.world;

/**
 * 雕刻器种子的线程内半径。
 *
 * <p>原版 {@code NoiseBasedChunkGenerator#applyCarvers} 用相邻区块的<b>绝对</b>坐标
 * {@code setLargeFeatureSeed(seed, x, z)} 播种。洞穴布局是相对区块原点的，所以只要种子按折回后的
 * 坐标取，副本的洞穴就逐块相同；{@code WorldgenRandom} 的 mixin 在这里读到半径后折回 x/z。
 */
public final class LoopCarverSeed {
    private static final ThreadLocal<Integer> RADIUS = new ThreadLocal<>();

    private LoopCarverSeed() {
    }

    public static void push(int radius) {
        if (radius > 0) {
            RADIUS.set(radius);
        } else {
            RADIUS.remove();
        }
    }

    public static void pop() {
        RADIUS.remove();
    }

    public static int current() {
        Integer radius = RADIUS.get();
        return radius == null ? 0 : radius;
    }
}
