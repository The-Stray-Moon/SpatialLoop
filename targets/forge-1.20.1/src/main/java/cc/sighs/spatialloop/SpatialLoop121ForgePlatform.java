package cc.sighs.spatialloop;

import cc.sighs.spatialloop.client.SpatialLoopForgeCurveConfig;
import cc.sighs.spatialloop.network.SpatialLoopForgeNetwork;
import cc.sighs.spatialloop.spi.LoopCurveSettings;
import cc.sighs.spatialloop.spi.LoopGenerationProfile;
import cc.sighs.spatialloop.spi.LoopNetworking;
import cc.sighs.spatialloop.spi.LoopServerContext;
import cc.sighs.spatialloop.spi.SpatialLoopPlatform;
import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge 1.20.1 的 SPI 实现。
 *
 * <p>1.20.1 的生成依赖参数：{@code ChunkStatus.maxDistance() = 12}，
 * {@code ChunkLevel.MAX_LEVEL = 33 + 12 = 45}，{@code ENTITY_TICKING = 31}，
 * 所以 halo = 14、最小半径 = 17。
 */
final class SpatialLoop121ForgePlatform implements SpatialLoopPlatform {
    static final SpatialLoop121ForgePlatform INSTANCE = new SpatialLoop121ForgePlatform();

    private static final LoopGenerationProfile GENERATION_PROFILE = new LoopGenerationProfile() {
        @Override
        public int generationRadius() {
            return 12;
        }

        @Override
        public int loadedHaloRadius() {
            return 14;
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

    private SpatialLoop121ForgePlatform() {
    }

    @Override
    public LoopGenerationProfile generationProfile() {
        return GENERATION_PROFILE;
    }

    @Override
    public LoopNetworking networking() {
        return SpatialLoopForgeNetwork::sendLoopRadius;
    }

    @Override
    public LoopServerContext serverContext() {
        return SERVER_CONTEXT;
    }

    @Override
    public LoopCurveSettings curveSettings() {
        return SpatialLoopForgeCurveConfig.INSTANCE;
    }
}
