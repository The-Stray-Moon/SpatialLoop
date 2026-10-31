package cc.sighs.spatialloop.spi;

/**
 * 一个 target 对共同代码暴露的运行时服务。
 *
 * <p>target 的加载器入口只负责注册事件/资源，并在启动时调用
 * {@link SpatialLoopServices#install(SpatialLoopPlatform)}；共同逻辑通过
 * {@link SpatialLoopServices#platform()} 取到具体实现。
 */
public interface SpatialLoopPlatform {
    LoopGenerationProfile generationProfile();

    LoopNetworking networking();

    LoopServerContext serverContext();

    LoopCurveSettings curveSettings();

    /** 尚未安装时使用：任何调用都说明 target 启动顺序错了。 */
    static SpatialLoopPlatform unavailable() {
        return new SpatialLoopPlatform() {
            @Override
            public LoopGenerationProfile generationProfile() {
                throw notInstalled();
            }

            @Override
            public LoopNetworking networking() {
                throw notInstalled();
            }

            @Override
            public LoopServerContext serverContext() {
                throw notInstalled();
            }

            @Override
            public LoopCurveSettings curveSettings() {
                throw notInstalled();
            }

            private IllegalStateException notInstalled() {
                return new IllegalStateException(
                        "SpatialLoop platform is not installed; the loader entry point must call "
                                + "SpatialLoopServices.install(...) during mod construction");
            }
        };
    }
}
