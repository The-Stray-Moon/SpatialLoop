package cc.sighs.spatialloop.geometry;

import java.util.Locale;

/**
 * 球面弯折的「模式 + 强度」换算成着色器实际使用的球面半径。
 *
 * <p>着色器用带符号的半径区分内外表面：正数把地形向下弯（球外表面），负数向上弯（球内表面），
 * 绝对值 {@code <= 1} 表示不弯折。这样顶点着色器里只有一个 {@code CurveRadius} uniform。
 *
 * <p>强度是曲率倍数：{@code 有效半径 = 基准半径 / 强度}，所以数值越大越弯。
 * 基准半径默认 256 格；启用自适应时改用当前维度保存的循环周期：
 * {@code 2πR = 周期}，即 {@code R = periodBlocks / 2π}，走完一圈正好绕球一周。
 */
public final class LoopCurveMath {
    /** 弯折模式，名称直接写进客户端配置。 */
    public enum Surface {
        /** 球外表面：地形随距离向下弯。 */
        OUTER_SURFACE,
        /** 球内表面：地形随距离向上弯。 */
        INNER_SURFACE,
        /** 不弯折。 */
        NONE;

        public String displayName() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** 不启用自适应、强度 1 时的球面半径（方块）。 */
    public static final double BASE_RADIUS_BLOCKS = 256.0;
    public static final double MIN_STRENGTH = 0.05;
    public static final double MAX_STRENGTH = 20.0;

    private LoopCurveMath() {
    }

    /**
     * @param surface    弯折模式
     * @param strength   曲率倍数，{@code 有效半径 = 基准半径 / strength}
     * @param adaptive   是否让基准半径跟随循环周期
     * @param loopRadius 当前维度的循环区块半径；0 表示该维度没有循环
     * @return 带符号的球面半径（方块）。{@code <= 1} 表示不弯折。
     */
    public static double radius(Surface surface, double strength, boolean adaptive, int loopRadius) {
        if (surface == null || surface == Surface.NONE) {
            return 1.0;
        }
        double base = adaptive && loopRadius > 0
                ? LoopGeometry.periodBlocks(loopRadius) / (2.0 * Math.PI)
                : BASE_RADIUS_BLOCKS;
        double safeStrength = Double.isFinite(strength)
                ? Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strength))
                : 1.0;
        double radius = base / safeStrength;
        return surface == Surface.INNER_SURFACE ? -radius : radius;
    }
}
