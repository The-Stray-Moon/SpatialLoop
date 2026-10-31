package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.geometry.LoopCurveMath;
import cc.sighs.spatialloop.spi.LoopCurveSettings;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric 的纯客户端配置：{@code config/spatialloop-client.properties}。
 *
 * <p>Fabric 没有内置配置 API；这里只实现与 NeoForge 版本相同的三个键
 * （surface / strength / adaptive）。文件不存在时使用默认值，并在首次读取时写出，
 * 方便玩家手工编辑。
 */
public final class SpatialLoopFabricCurveConfig implements LoopCurveSettings {
    public static final SpatialLoopFabricCurveConfig INSTANCE = new SpatialLoopFabricCurveConfig();

    private static final String FILE_NAME = "spatialloop-client.properties";
    private static final String SURFACE = "surface";
    private static final String STRENGTH = "strength";
    private static final String ADAPTIVE = "adaptive";

    private boolean loaded;
    private LoopCurveMath.Surface surface = LoopCurveMath.Surface.OUTER_SURFACE;
    private double strength = 1.0;
    private boolean adaptive;

    private SpatialLoopFabricCurveConfig() {
    }

    @Override
    public LoopCurveMath.Surface surface() {
        load();
        return this.surface;
    }

    @Override
    public double strength() {
        load();
        return this.strength;
    }

    @Override
    public boolean adaptive() {
        load();
        return this.adaptive;
    }

    private synchronized void load() {
        if (this.loaded) {
            return;
        }
        this.loaded = true;
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ignored) {
                return;
            }
        } else {
            writeDefaults(path, properties);
        }
        this.surface = parseSurface(properties.getProperty(SURFACE));
        this.strength = parseStrength(properties.getProperty(STRENGTH));
        this.adaptive = Boolean.parseBoolean(properties.getProperty(ADAPTIVE, "false"));
    }

    private static void writeDefaults(Path path, Properties properties) {
        properties.setProperty(SURFACE, LoopCurveMath.Surface.OUTER_SURFACE.name());
        properties.setProperty(STRENGTH, "1.0");
        properties.setProperty(ADAPTIVE, "false");
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "SpatialLoop client-only spherical bending settings");
            }
        } catch (IOException ignored) {
            // 只读环境照样使用内存里的默认值。
        }
    }

    private static LoopCurveMath.Surface parseSurface(String value) {
        if (value == null) {
            return LoopCurveMath.Surface.OUTER_SURFACE;
        }
        try {
            return LoopCurveMath.Surface.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LoopCurveMath.Surface.OUTER_SURFACE;
        }
    }

    private static double parseStrength(String value) {
        if (value == null) {
            return 1.0;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed)
                    ? Math.max(LoopCurveMath.MIN_STRENGTH, Math.min(LoopCurveMath.MAX_STRENGTH, parsed))
                    : 1.0;
        } catch (NumberFormatException ignored) {
            return 1.0;
        }
    }
}
