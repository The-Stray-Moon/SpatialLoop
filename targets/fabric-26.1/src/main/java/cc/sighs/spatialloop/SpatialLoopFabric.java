package cc.sighs.spatialloop;

import cc.sighs.spatialloop.network.SpatialLoopFabricNetwork;
import cc.sighs.spatialloop.spi.SpatialLoopServices;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** Fabric 26.1 入口：只做加载器注册，世界生成/存档/渲染逻辑与 NeoForge 版本共享。 */
public final class SpatialLoopFabric implements ModInitializer {
    public static final Identifier PERIODIC_GENERATOR =
            Identifier.fromNamespaceAndPath(SpatialLoop.MOD_ID, "periodic");

    @Override
    public void onInitialize() {
        SpatialLoopServices.install(SpatialLoop261FabricPlatform.INSTANCE);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, PERIODIC_GENERATOR, SpatialLoopChunkGenerator.CODEC);
        SpatialLoopFabricNetwork.registerPayloads();
        ServerLifecycleEvents.SERVER_STARTED.register(SpatialLoop261FabricPlatform::setServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SpatialLoop261FabricPlatform.setServer(null));
    }
}
