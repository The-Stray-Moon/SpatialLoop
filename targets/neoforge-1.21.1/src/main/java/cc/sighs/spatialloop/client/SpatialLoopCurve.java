package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.geometry.LoopCurveMath;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * 地形/世界球面弯折的客户端参数。
 *
 * <p>着色器把「相机相对的世界坐标」按弧长贴到半径 R 的球面上：
 * 水平距离 d 对应极角 d / R，竖直位移 {@code -R(1 - cos θ)}。
 * R 的符号表示内外表面（正=向下弯，负=向上弯），{@code |R| <= 1} 表示不弯。
 *
 * <p>R 由 {@link SpatialLoopCurveConfig} 的「模式 / 强度 / 自适应」决定，
 * 见 {@link LoopCurveMath#radius}。自适应需要当前维度的循环半径，由服务端通过
 * {@code spatialloop:loop_radius} 载荷在登录/换维度时推送；客户端本身拿不到生成器。
 */
public final class SpatialLoopCurve {
    public static final double DEFAULT_RADIUS_BLOCKS = LoopCurveMath.BASE_RADIUS_BLOCKS;

    /** 渲染线程专用，避免每个 shader 每次 apply 都分配矩阵。 */
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();
    private static final Quaternionf VIEW_QUATERNION = new Quaternionf();

    /** 服务端推送的当前维度循环半径；0 = 未知/该维度没有循环。 */
    private static volatile int serverLoopRadius;

    /** 测试用调试覆盖；非 null 时忽略配置。 */
    private static volatile Double debugRadius;

    private SpatialLoopCurve() {
    }

    /** 着色器使用的带符号半径：正=球外表面，负=球内表面，|r|<=1=不弯。 */
    public static float radius() {
        Double override = debugRadius;
        if (override != null) {
            return override.floatValue();
        }
        return (float) LoopCurveMath.radius(
                SpatialLoopCurveConfig.surface(),
                SpatialLoopCurveConfig.strength(),
                SpatialLoopCurveConfig.adaptive(),
                serverLoopRadius
        );
    }

    /** 服务端网络载荷回调（主线程）。 */
    public static void setServerLoopRadius(int radius) {
        serverLoopRadius = radius;
    }

    /** 仅用于开发期截图/回归；生产环境请改配置。 */
    public static void setRadius(double blocks) {
        debugRadius = blocks;
    }

    public static void clearRadiusOverride() {
        debugRadius = null;
    }

    /**
     * 世界→视图的旋转矩阵（不含平移），与 {@code GameRenderer.renderLevel} 交给 {@code LevelRenderer}
     * 的矩阵完全同源；实体/粒子着色器用它把视图空间坐标还原成相机相对世界坐标。
     */
    public static Matrix4f viewRotation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) {
            return VIEW_ROTATION.identity();
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        return VIEW_ROTATION.rotation(camera.rotation().conjugate(VIEW_QUATERNION));
    }
}
