package cc.sighs.spatialloop;

import cc.sighs.spatialloop.client.SpatialLoopCurveConfig;
import cc.sighs.spatialloop.network.SpatialLoopNetwork;
import cc.sighs.spatialloop.spi.LoopCurveSettings;
import cc.sighs.spatialloop.spi.LoopGenerationProfile;
import cc.sighs.spatialloop.spi.LoopNetworking;
import cc.sighs.spatialloop.spi.LoopServerContext;
import cc.sighs.spatialloop.spi.SpatialLoopPlatform;
import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * NeoForge 1.21.1 对共同 SPI 的实现。
 *
 * <p>这里只放「这个版本/加载器确实不同」的部分：生成依赖常量、载荷发送、当前服务器查询。
 * 视距/最小半径的公式在 common 的 {@code LoopLoadingLimits} 里。
 *
 * <p>1.21.1 的常量来源：{@code ChunkPyramid.GENERATION_PYRAMID.getStepTo(FULL)
 * .getAccumulatedRadiusOf(EMPTY) == 11}；{@code ChunkLevel.MAX_LEVEL - ENTITY_TICKING
 * = 33 + 11 - 31 = 13}。{@code ChunkMapOwnershipMixin} 启动循环维度时仍会用运行时
 * {@code LoopGenerationReach.forStatus(FULL)} 复核 11。
 */
final class SpatialLoop121Platform implements SpatialLoopPlatform {
    static final SpatialLoop121Platform INSTANCE = new SpatialLoop121Platform();

    private static final LoopGenerationProfile GENERATION_PROFILE = new LoopGenerationProfile() {
        @Override
        public int generationRadius() {
            return 11;
        }

        @Override
        public int loadedHaloRadius() {
            return 13;
        }
    };

    private static final LoopServerContext SERVER_CONTEXT = new LoopServerContext() {
        @Override
        public int overworldLoopRadius() {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) {
                return 0;
            }
            ServerLevel overworld = server.overworld();
            return overworld == null ? 0 : LoopLevel.radiusOf(overworld);
        }
    };

    private SpatialLoop121Platform() {
    }

    @Override
    public LoopGenerationProfile generationProfile() {
        return GENERATION_PROFILE;
    }

    @Override
    public LoopNetworking networking() {
        return SpatialLoopNetwork::sendLoopRadius;
    }

    @Override
    public LoopServerContext serverContext() {
        return SERVER_CONTEXT;
    }

    @Override
    public LoopCurveSettings curveSettings() {
        return SpatialLoopCurveConfig.INSTANCE;
    }
}
