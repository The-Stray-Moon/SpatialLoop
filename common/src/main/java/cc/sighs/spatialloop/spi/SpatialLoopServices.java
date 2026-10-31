package cc.sighs.spatialloop.spi;

import java.util.Objects;

/** 进程内的 SPI 注册点；由各 target 的加载器入口安装唯一实现。 */
public final class SpatialLoopServices {
    private static volatile SpatialLoopPlatform platform = SpatialLoopPlatform.unavailable();

    private SpatialLoopServices() {
    }

    public static void install(SpatialLoopPlatform implementation) {
        platform = Objects.requireNonNull(implementation, "implementation");
    }

    public static SpatialLoopPlatform platform() {
        return platform;
    }
}
