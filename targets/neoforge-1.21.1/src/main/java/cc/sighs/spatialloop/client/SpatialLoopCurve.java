package cc.sighs.spatialloop.client;

/**
 * 地形球面弯折的客户端参数。
 *
 * <p>着色器把「相机相对的世界坐标」按弧长贴到半径 {@code radius} 的球面上：
 * 水平距离 {@code d} 对应极角 {@code d / R}，竖直下落 {@code R(1 - cos θ)}。
 * 半径越小球感越强；{@code <= 1} 时不着色器直接保持原样（平地）。
 *
 * <p>客户端拿不到存档里的循环半径（{@code ClientLevel} 不含生成器），所以这是独立的视觉参数，
 * 可用 JVM 属性 {@code -Dspatialloop.curveRadius=<方块>} 覆盖，默认 256。
 */
public final class SpatialLoopCurve {
    public static final double DEFAULT_RADIUS_BLOCKS = 256.0;

    private static volatile double radius = readRadius();

    private SpatialLoopCurve() {
    }

    private static double readRadius() {
        String override = System.getProperty("spatialloop.curveRadius");
        if (override != null) {
            try {
                return Double.parseDouble(override);
            } catch (NumberFormatException ignored) {
                // 保持默认值；非法参数不应让客户端启动失败。
            }
        }
        return DEFAULT_RADIUS_BLOCKS;
    }

    public static float radius() {
        return (float) radius;
    }

    public static void setRadius(double blocks) {
        radius = blocks;
    }
}
