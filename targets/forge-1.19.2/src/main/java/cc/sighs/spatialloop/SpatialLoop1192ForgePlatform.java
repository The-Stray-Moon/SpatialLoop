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
 * Forge 1.19.2 的 SPI 实现。
 *
 * <p>1.19.2 的生成依赖参数：{@code ChunkStatus.maxDistance() = 12}（{@code STATUS_BY_RANGE} 长度），
 * {@code ChunkMap.MAX_CHUNK_DISTANCE = 33 + 12 = 45}；玩家票据等级是
 * {@code max(0, 31 - simulationDistance)}，所以票据方形之外还有 14 圈会被原版保留/生成
 * （halo = 45 - 31 = 14，与 1.20.1 相同），最小半径 = 14 + 2 + 1 = 17。
 */
final class SpatialLoop1192ForgePlatform implements SpatialLoopPlatform {
    static final SpatialLoop1192ForgePlatform INSTANCE = new SpatialLoop1192ForgePlatform();

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

    private SpatialLoop1192ForgePlatform() {
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
