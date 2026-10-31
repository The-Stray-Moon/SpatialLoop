package cc.sighs.spatialloop.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Set;

/**
 * 把球面弯折注入到所有世界空间顶点着色器。
 *
 * <p>地形着色器有 {@code ChunkOffset}，可以直接拿到相机相对世界坐标；实体、方块实体、粒子、文字等
 * 没有这个 uniform，只能先按原版矩阵算出视图空间坐标，再用视角旋转的转置还原成相机相对世界坐标：
 * {@code rel = transpose(V) * (ModelViewMat * Position)}，弯折后乘回 {@code V}。
 *
 * <p>着色器源码在 {@code ShaderInstance} 读取资源时统一改写，所以新增世界渲染类型只需要在
 * {@link #WORLD_SHADERS} 里加名字，不用复制整套原版 shader 资源。
 *
 * <p>GUI/文字共用同一个 shader 的情形（物品栏文字、name tag）用投影矩阵区分：
 * 透视投影的 {@code ProjMat[3][3] == 0}，正交投影是 1；只在透视时弯折。
 *
 * <p>1.18.2 同样是 shader JSON + {@code ShaderInstance} uniform 体系：地形着色器资源按 1.18.2 原版重写
 * （{@code fog_distance(ModelViewMat, pos, FogShape)}、{@code out vec4 normal}，JSON 保留
 * {@code blend}/{@code attributes}），只多一个 {@code CurveRadius}。
 */
public final class SpatialLoopShaderPatch {
    /** 直接按世界坐标弯折的渲染类型（含 GUI/世界共用的文字 shader，靠投影矩阵区分）。 */
    private static final Set<String> WORLD_SHADERS = Set.of(
            "particle",
            "rendertype_armor_cutout_no_cull",
            "rendertype_armor_entity_glint",
            "rendertype_armor_glint",
            "rendertype_beacon_beam",
            "rendertype_crumbling",
            "rendertype_end_portal",
            "rendertype_energy_swirl",
            "rendertype_entity_alpha",
            "rendertype_entity_cutout",
            "rendertype_entity_cutout_no_cull",
            "rendertype_entity_cutout_no_cull_z_offset",
            "rendertype_entity_decal",
            "rendertype_entity_glint",
            "rendertype_entity_glint_direct",
            "rendertype_entity_no_outline",
            "rendertype_entity_shadow",
            "rendertype_entity_smooth_cutout",
            "rendertype_entity_solid",
            "rendertype_entity_translucent",
            "rendertype_entity_translucent_cull",
            "rendertype_entity_translucent_emissive",
            "rendertype_eyes",
            "rendertype_item_entity_translucent_cull",
            "rendertype_leash",
            "rendertype_lightning",
            "rendertype_outline",
            "rendertype_text",
            "rendertype_text_background",
            "rendertype_text_background_see_through",
            "rendertype_text_intensity",
            "rendertype_text_intensity_see_through",
            "rendertype_text_see_through",
            "rendertype_translucent_moving_block",
            "rendertype_water_mask"
    );

    /** 线段包围盒：位置计算带 NDC 线宽展开，需要单独改写。 */
    private static final String LINES = "rendertype_lines";

    private static final String POSITION_LINE = "gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);";
    private static final String LINE_START = "vec4 linePosStart = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position, 1.0);";
    private static final String LINE_END = "vec4 linePosEnd = ProjMat * VIEW_SCALE * ModelViewMat * vec4(Position + Normal, 1.0);";

    private static final String DECLARATIONS = """
            uniform mat4 ViewRot;
            uniform float CurveRadius;

            /** Camera-relative world position -> sphere of radius |CurveRadius|; negative bends the inner surface. */
            vec3 spatialloop_bend(vec3 pos) {
                float d = length(pos.xz);
                if (d < 1.0e-4 || abs(CurveRadius) <= 1.0) {
                    return pos;
                }
                float theta = d / CurveRadius;
                float scale = sin(theta) / theta;
                return vec3(pos.x * scale, pos.y - CurveRadius * (1.0 - cos(theta)), pos.z * scale);
            }
            """;

    /** 每一块被改写的 shader 都先用投影矩阵判断是否世界渲染：透视才弯折，GUI 正交保持原样。 */
    private static final String PERSPECTIVE_BLOCK = """
            vec4 sl_view = ModelViewMat * vec4(Position, 1.0);
            if (ProjMat[3][3] == 0.0) {
                vec3 sl_rel = spatialloop_bend(transpose(mat3(ViewRot)) * sl_view.xyz);
                gl_Position = ProjMat * vec4(mat3(ViewRot) * sl_rel, 1.0);
            } else {
                gl_Position = ProjMat * sl_view;
            }""";

    private static final String LINES_BLOCK = """
            vec4 sl_start = ModelViewMat * vec4(Position, 1.0);
            vec4 sl_end = ModelViewMat * vec4(Position + Normal, 1.0);
            vec3 sl_start_rel = transpose(mat3(ViewRot)) * sl_start.xyz;
            vec3 sl_end_rel = transpose(mat3(ViewRot)) * sl_end.xyz;
            if (ProjMat[3][3] == 0.0) {
                sl_start_rel = spatialloop_bend(sl_start_rel);
                sl_end_rel = spatialloop_bend(sl_end_rel);
            }
            vec4 linePosStart = ProjMat * VIEW_SCALE * vec4(mat3(ViewRot) * sl_start_rel, 1.0);
            vec4 linePosEnd = ProjMat * VIEW_SCALE * vec4(mat3(ViewRot) * sl_end_rel, 1.0);""";

    private static final Gson GSON = new Gson();

    private SpatialLoopShaderPatch() {
    }

    /** {@code shaders/core/foo.vsh} -> {@code foo}；非 minecraft 命名空间返回 null。 */
    public static String shaderName(String namespace, String path) {
        if (!"minecraft".equals(namespace) || !path.startsWith("shaders/core/")) {
            return null;
        }
        String file = path.substring("shaders/core/".length());
        int dot = file.lastIndexOf('.');
        return dot < 0 ? null : file.substring(0, dot);
    }

    public static boolean isWorldShader(String name) {
        return name != null && (WORLD_SHADERS.contains(name) || LINES.equals(name));
    }

    /** 给 shader JSON 补上 {@code ViewRot}/{@code CurveRadius} 两个 uniform。 */
    public static String patchJson(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        JsonArray uniforms = object.has("uniforms")
                ? object.getAsJsonArray("uniforms")
                : new JsonArray();
        if (findUniform(uniforms, "ViewRot") == null) {
            JsonObject matrix = new JsonObject();
            matrix.addProperty("name", "ViewRot");
            matrix.addProperty("type", "matrix4x4");
            matrix.addProperty("count", 16);
            JsonArray values = new JsonArray();
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    values.add(i == j ? 1.0 : 0.0);
                }
            }
            matrix.add("values", values);
            uniforms.add(matrix);
        }
        if (findUniform(uniforms, "CurveRadius") == null) {
            JsonObject curve = new JsonObject();
            curve.addProperty("name", "CurveRadius");
            curve.addProperty("type", "float");
            curve.addProperty("count", 1);
            JsonArray values = new JsonArray();
            values.add(1.0E9);
            curve.add("values", values);
            uniforms.add(curve);
        }
        if (!object.has("uniforms")) {
            object.add("uniforms", uniforms);
        }
        return GSON.toJson(object);
    }

    private static JsonObject findUniform(JsonArray uniforms, String name) {
        for (int i = 0; i < uniforms.size(); i++) {
            JsonObject uniform = uniforms.get(i).getAsJsonObject();
            if (name.equals(uniform.get("name").getAsString())) {
                return uniform;
            }
        }
        return null;
    }

    /**
     * 改写顶点着色器：先插入声明/函数，再替换位置计算。
     * 源码结构与固定版本不符时原样返回，由调用方决定是否记录诊断。
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
            patched = source.replace(LINE_START, LINES_BLOCK).replace(LINE_END, "");
        } else {
            if (!source.contains(POSITION_LINE)) {
                return source;
            }
            // 所有名单内的 shader 都用同一段带透视判定的替换：GUI 正交投影走 else 分支。
            patched = source.replace(POSITION_LINE, PERSPECTIVE_BLOCK);
        }
        int main = patched.indexOf("void main()");
        if (main < 0) {
            return source;
        }
        return patched.substring(0, main) + DECLARATIONS + "\n" + patched.substring(main);
    }
}
