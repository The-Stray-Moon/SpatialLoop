package cc.sighs.spatialloop;

import cc.sighs.spatialloop.client.SpatialLoopFabricCurveConfig;
import cc.sighs.spatialloop.network.SpatialLoopFabricNetwork;
import cc.sighs.spatialloop.spi.LoopCurveSettings;
import cc.sighs.spatialloop.spi.LoopGenerationProfile;
import cc.sighs.spatialloop.spi.LoopNetworking;
import cc.sighs.spatialloop.spi.LoopServerContext;
import cc.sighs.spatialloop.spi.SpatialLoopPlatform;
import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Fabric 26.1 的 SPI 实现；生成依赖常量与 NeoForge 26.1 相同（FULL reach = 11，halo = 13）。 */
final class SpatialLoop261FabricPlatform implements SpatialLoopPlatform {
    static final SpatialLoop261FabricPlatform INSTANCE = new SpatialLoop261FabricPlatform();

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
            MinecraftServer server = currentServer;
            if (server == null) {
                return 0;
            }
            ServerLevel overworld = server.overworld();
            return overworld == null ? 0 : LoopLevel.radiusOf(overworld);
        }
    };

    private static volatile MinecraftServer currentServer;

    private SpatialLoop261FabricPlatform() {
    }

    static void setServer(MinecraftServer server) {
        currentServer = server;
    }

    @Override
    public LoopGenerationProfile generationProfile() {
        return GENERATION_PROFILE;
    }

    @Override
    public LoopNetworking networking() {
        return SpatialLoopFabricNetwork::sendLoopRadius;
    }

    @Override
    public LoopServerContext serverContext() {
        return SERVER_CONTEXT;
    }

    @Override
    public LoopCurveSettings curveSettings() {
        return SpatialLoopFabricCurveConfig.INSTANCE;
    }
}
