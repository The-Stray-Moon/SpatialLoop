package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.geometry.LoopCurveMath;
import cc.sighs.spatialloop.spi.LoopCurveSettings;
import net.minecraftforge.common.ForgeConfigSpec;

/** Forge 1.19.2 的客户端弯折配置（{@code config/spatialloop-client.toml}）。 */
public final class SpatialLoopForgeCurveConfig implements LoopCurveSettings {
    public static final SpatialLoopForgeCurveConfig INSTANCE = new SpatialLoopForgeCurveConfig();

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.EnumValue<LoopCurveMath.Surface> SURFACE = BUILDER
            .comment(
                    "球面弯折模式：OUTER_SURFACE / INNER_SURFACE / NONE。",
                    "只影响客户端渲染，不影响服务端世界数据。"
            )
            .defineEnum("surface", LoopCurveMath.Surface.OUTER_SURFACE);

    public static final ForgeConfigSpec.DoubleValue STRENGTH = BUILDER
            .comment("曲率倍数：有效半径 = 基准半径 / strength。")
            .defineInRange("strength", 1.0, LoopCurveMath.MIN_STRENGTH, LoopCurveMath.MAX_STRENGTH);

    public static final ForgeConfigSpec.BooleanValue ADAPTIVE = BUILDER
            .comment("基准半径是否跟随当前维度的循环半径（周期 / 2π）。")
            .define("adaptive", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private SpatialLoopForgeCurveConfig() {
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
