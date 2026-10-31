package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.geometry.LoopCurveMath;
import cc.sighs.spatialloop.spi.LoopCurveSettings;
import cc.sighs.spatialloop.spi.SpatialLoopServices;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

/**
 * 地形/世界球面弯折的客户端参数（1.18.2 版）。
 *
 * <p>1.18.2 没有 JOML，相机矩阵与 uniform 都是 {@code com.mojang.math} 类型：
 * 视角旋转的共轭用 {@code Quaternion(-i, -j, -k, r)} 手工构造，再乘以
 * {@link Matrix4f#multiply(Quaternion)}。弯折公式与其它版本完全相同。
 *
 * <p>R 由 target 提供的 {@link LoopCurveSettings}（模式 / 强度 / 自适应）决定，
 * 见 {@link LoopCurveMath#radius}。自适应需要当前维度的循环半径，由服务端通过
 * {@code spatialloop:loop_radius} 载荷在登录/换维度时推送；客户端本身拿不到生成器。
 */
public final class SpatialLoopCurve {
    public static final double DEFAULT_RADIUS_BLOCKS = LoopCurveMath.BASE_RADIUS_BLOCKS;

    /** 渲染线程专用，避免每个 shader 每次 apply 都分配矩阵。 */
    private static final Matrix4f VIEW_ROTATION = new Matrix4f();

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
        LoopCurveSettings settings = SpatialLoopServices.platform().curveSettings();
        return (float) LoopCurveMath.radius(
                settings.surface(),
                settings.strength(),
                settings.adaptive(),
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
     * 的矩阵同源；实体/粒子着色器用它把视图空间坐标还原成相机相对世界坐标。
     */
    public static Matrix4f viewRotation() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer == null) {
            VIEW_ROTATION.setIdentity();
            return VIEW_ROTATION;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Quaternion rotation = camera.rotation();
        // 共轭 = 反向旋转；1.18.2 的 Quaternion 没有 conj()，按分量取负。
        Quaternion inverse = new Quaternion(-rotation.i(), -rotation.j(), -rotation.k(), rotation.r());
        VIEW_ROTATION.setIdentity();
        VIEW_ROTATION.multiply(inverse);
        return VIEW_ROTATION;
    }
}
