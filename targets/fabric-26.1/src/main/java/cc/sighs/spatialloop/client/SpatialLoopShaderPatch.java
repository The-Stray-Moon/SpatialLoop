package cc.sighs.spatialloop.client;

import com.mojang.logging.LogUtils;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;

/**
 * 把球面弯折注入到所有世界空间顶点着色器。
 *
 * <p>26.1 的渲染管线换成了 {@code RenderPipeline} + GLSL 源码：既没有 shader JSON，也不支持
 * 自定义 uniform block（{@code GlProgram.setupUniforms} 只认管线里声明过的 block 和
 * {@code Projection}/{@code Lighting}/{@code Fog}/{@code Globals} 四个内置 block，
 * 其它 block 会被当成「unknown and unsupported uniform」直接不绑定）。所以 1.21.1 那套
 * 「JSON 补 {@code ViewRot}/{@code CurveRadius}，在 {@code ShaderInstance#apply} 里上传」的做法
 * 不再可行，改为在 {@code GlDevice#compileShader} 取到源码后做两件事：
 *
 * <ul>
 *   <li>弯折本身仍是 GLSL 改写（{@link #patchVertex}）；</li>
 *   <li>半径以 {@code const} 常量烘进源码，半径变化时由 {@code ShaderInstanceCurveMixin}
 *       清空管线缓存触发重编译（{@link #takeRecompileRequest}）。</li>
 * </ul>
 *
 * <p>26.1 的顶点着色器已经把相机相对世界坐标算好了：地形是
 * {@code pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset}，方块是
 * {@code Position + ModelOffset}，实体/粒子/文字直接用 {@code Position}（雾距离就是从它算的）。
 * 因此不需要再把视图空间坐标用视角旋转还原成世界坐标，也就不再需要 {@code ViewRot}。
 *
 * <p>26.1 的 GUI、物品栏物品、name tag 与对应的世界渲染共用同一批着色器文件，仍然用投影矩阵区分：
 * 透视投影的 {@code ProjMat[3][3] == 0}，正交投影是 1；只在透视时弯折。
 */
public final class SpatialLoopShaderPatch {
    /**
     * 需要弯折的世界空间着色器（{@code shaders/core/<name>.vsh} 的 {@code <name>}）。
     *
     * <p>天空（{@code sky}/{@code stars}）、云、世界边界、全景图不在此列；GUI（{@code gui}）只走正交投影，
     * 由着色器内的 {@code ProjMat[3][3]} 判定排除。
     */
    private static final Set<String> WORLD_SHADERS = Set.of(
            // 地形与方块
            "terrain",
            "block",
            // 实体、方块实体、物品、附魔光效、粒子
            "entity",
            "item",
            "glint",
            "particle",
            // 具体世界渲染类型
            "rendertype_beacon_beam",
            "rendertype_crumbling",
            "rendertype_end_portal",
            "rendertype_entity_shadow",
            "rendertype_leash",
            "rendertype_lightning",
            "rendertype_outline",
            "rendertype_water_mask",
            // 文字（含 GUI/世界共用，靠投影矩阵区分）
            "rendertype_text",
            "rendertype_text_background",
            "rendertype_text_background_see_through",
            "rendertype_text_intensity",
            "rendertype_text_intensity_see_through",
            "rendertype_text_see_through",
            // 通用位置着色器（调试形状、gizmo、部分 GUI 也用它）
            "position",
            "position_color",
            "position_tex",
            "position_tex_color",
            "debug_point"
    );

    /** 线段包围盒：位置计算带 NDC 线宽展开，需要单独改写。 */
    private static final String LINES = "rendertype_lines";

    /** 位置已经是相机相对世界坐标的着色器。 */
    private static final String POSITION_LINE = "gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);";
    /** 位置由着色器自己算进局部变量 {@code pos} 的着色器（{@code terrain}/{@code block}）。 */
    private static final String LOCAL_POSITION_LINE = "gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);";

    private static final String POSITION_PATCH =
            "gl_Position = ProjMat * ModelViewMat * vec4(ProjMat[3][3] == 0.0 ? spatialloop_bend(Position) : Position, 1.0);";
    private static final String LOCAL_POSITION_PATCH =
            "gl_Position = ProjMat * ModelViewMat * vec4(ProjMat[3][3] == 0.0 ? spatialloop_bend(pos) : pos, 1.0);";

    private static final String LINE_START =
            "vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position, 1.0);";
    private static final String LINE_END =
            "vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position + Normal, 1.0);";
    private static final String LINES_PATCH = """
            vec3 spatialloop_line_start = ProjMat[3][3] == 0.0 ? spatialloop_bend(Position) : Position;
                vec3 spatialloop_line_end = ProjMat[3][3] == 0.0 ? spatialloop_bend(Position + Normal) : Position + Normal;
                vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(spatialloop_line_start, 1.0);
                vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(spatialloop_line_end, 1.0);""";

    /** 曲线半径没有配置时的回退值：{@code |R| <= 1} 表示不弯折。 */
    private static final float NO_BEND_RADIUS = 1.0E9F;

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 最近一次烘进源码的弯折半径；NaN 表示还没有改写过着色器。 */
    private static volatile float bakedRadius = Float.NaN;

    /** 平台未安装的告警只打一次，免得每次重编译都刷屏。 */
    private static volatile boolean reportedMissingPlatform;

    private SpatialLoopShaderPatch() {
    }

    /** {@code core/entity}（26.1 的 shader id 路径）-> {@code entity}；非 minecraft 命名空间返回 null。 */
    public static String shaderName(String namespace, String path) {
        if (!"minecraft".equals(namespace) || !path.startsWith("core/")) {
            return null;
        }
        return path.substring("core/".length());
    }

    public static boolean isWorldShader(String name) {
        return name != null && (WORLD_SHADERS.contains(name) || LINES.equals(name));
    }

    /**
     * 改写顶点着色器：先插入弯折函数，再把位置计算换成弯折后的坐标。
     * 源码结构与固定版本不符时原样返回（此时不会记录已烘入的半径）。
     */
    public static String patchVertex(String name, String source) {
        if (!isWorldShader(name) || source.contains("spatialloop_bend(")) {
            return source;
        }
        String patched;
        if (LINES.equals(name)) {
            if (!source.contains(LINE_START) || !source.contains(LINE_END)) {
                return source;
            }
            patched = source.replace(LINE_START, LINES_PATCH).replace(LINE_END, "");
        } else if (source.contains(POSITION_LINE)) {
            patched = source.replace(POSITION_LINE, POSITION_PATCH);
        } else if (source.contains(LOCAL_POSITION_LINE)) {
            patched = source.replace(LOCAL_POSITION_LINE, LOCAL_POSITION_PATCH);
        } else {
            return source;
        }
        int main = patched.indexOf("void main()");
        if (main < 0) {
            return source;
        }
        float radius = currentRadius();
        bakedRadius = radius;
        return patched.substring(0, main) + declarations(radius) + "\n" + patched.substring(main);
    }

    /**
     * 半径变了就返回 true，并把「已烘进源码的半径」更新为当前值。
     *
     * <p>调用方（{@code ShaderInstanceCurveMixin}）负责清空管线缓存，让着色器按新常量重新编译。
     * 还没改写过着色器时返回 false：此时管线里没有任何烘了旧值的程序。
     */
    public static boolean takeRecompileRequest() {
        float baked = bakedRadius;
        float current = currentRadius();
        if (Float.isNaN(baked) || Float.floatToIntBits(baked) == Float.floatToIntBits(current)) {
            return false;
        }
        bakedRadius = current;
        return true;
    }

    /**
     * 当前弯折半径。
     *
     * <p>NaN/Infinity 会让 GLSL 编译失败，回退成不弯折；平台尚未安装（极早的着色器编译）同样回退，
     * 由首帧的半径同步触发重编译，不因为这里的异常弄坏着色器编译。
     */
    private static float currentRadius() {
        try {
            float radius = SpatialLoopCurve.radius();
            return Float.isFinite(radius) ? radius : NO_BEND_RADIUS;
        } catch (RuntimeException platformNotInstalled) {
            if (!reportedMissingPlatform) {
                reportedMissingPlatform = true;
                LOGGER.warn("SpatialLoop curve settings are unavailable; world shaders stay unbent until they are", platformNotInstalled);
            }
            return NO_BEND_RADIUS;
        }
    }

    private static String declarations(float radius) {
        return """
                const float spatialloop_curve_radius = %s;

                /** Camera-relative world position -> sphere of radius |spatialloop_curve_radius|; negative bends the inner surface. */
                vec3 spatialloop_bend(vec3 pos) {
                    float d = length(pos.xz);
                    if (d < 1.0e-4 || abs(spatialloop_curve_radius) <= 1.0) {
                        return pos;
                    }
                    float theta = d / spatialloop_curve_radius;
                    float scale = sin(theta) / theta;
                    return vec3(pos.x * scale, pos.y - spatialloop_curve_radius * (1.0 - cos(theta)), pos.z * scale);
                }
                """.formatted(String.format(Locale.ROOT, "%.4f", radius));
    }
}
