package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.geometry.LoopCurveMath;
import cc.sighs.spatialloop.spi.LoopCurveSettings;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 纯客户端配置（{@code config/spatialloop-client.toml}）。
 *
 * <ul>
 *   <li>{@code surface}：球外表面 / 球内表面 / 不弯折；</li>
 *   <li>{@code strength}：曲率倍数，有效半径 = 基准半径 / strength；</li>
 *   <li>{@code adaptive}：基准半径是否跟随当前维度的循环半径（周期 / 2π）。</li>
 * </ul>
 *
 * <p>循环半径是服务端存档属性，客户端通过 {@code LoopRadiusPayload} 收到；没收到或该维度
 * 没有循环时自适应回退到默认基准半径。
 */
public final class SpatialLoopCurveConfig implements LoopCurveSettings {
    /** SPI 使用的单例；实际值仍由 NeoForge 的 config spec 提供。 */
    public static final SpatialLoopCurveConfig INSTANCE = new SpatialLoopCurveConfig();

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<LoopCurveMath.Surface> SURFACE = BUILDER
            .comment(
                    "球面弯折模式：",
                    "OUTER_SURFACE = 球外表面（地形随距离向下弯）",
                    "INNER_SURFACE = 球内表面（地形随距离向上弯）",
                    "NONE = 不弯折"
            )
            .defineEnum("surface", LoopCurveMath.Surface.OUTER_SURFACE);

    public static final ModConfigSpec.DoubleValue STRENGTH = BUILDER
            .comment(
                    "球面弯折强度（曲率倍数，越大越弯）：有效半径 = 基准半径 / strength。",
                    "基准半径：不启用自适应时 256 格；启用自适应时 = 循环周期 / 2π。",
                    "只影响客户端渲染，不影响服务端世界数据。"
            )
            .defineInRange("strength", 1.0, LoopCurveMath.MIN_STRENGTH, LoopCurveMath.MAX_STRENGTH);

    public static final ModConfigSpec.BooleanValue ADAPTIVE = BUILDER
            .comment(
                    "球面弯折强度自适应：基准半径跟随当前维度保存的空间循环半径（周期 / 2π）。",
                    "该维度没有循环（例如下界/末地）时回退到 256 格。"
            )
            .define("adaptive", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private SpatialLoopCurveConfig() {
    }

    @Override
    public LoopCurveMath.Surface surface() {
        return SPEC.isLoaded() ? SURFACE.get() : LoopCurveMath.Surface.OUTER_SURFACE;
    }

    @Override
    public double strength() {
        return SPEC.isLoaded() ? STRENGTH.get() : 1.0;
    }

    @Override
    public boolean adaptive() {
        return SPEC.isLoaded() && ADAPTIVE.get();
    }
}
