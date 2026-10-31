package cc.sighs.spatialloop;

import cc.sighs.spatialloop.network.SpatialLoopFabricNetwork;
import cc.sighs.spatialloop.spi.SpatialLoopServices;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Fabric 1.20.1 入口：只做加载器注册，世界生成/存档/渲染逻辑与其它 target 共享。
 *
 * <p>Fabric 没有 NeoForge 的 {@code DeferredRegister}，这里直接在模组初始化时注册生成器 codec；
 * 服务器实例通过 Fabric API 的生命周期事件交给 SPI。
 */
public final class SpatialLoopFabric implements ModInitializer {
    public static final ResourceLocation PERIODIC_GENERATOR =
            new ResourceLocation(SpatialLoop.MOD_ID, "periodic");

    @Override
    public void onInitialize() {
        SpatialLoopServices.install(SpatialLoop121FabricPlatform.INSTANCE);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, PERIODIC_GENERATOR, SpatialLoopChunkGenerator.CODEC);
        SpatialLoopFabricNetwork.registerPayloads();
        ServerLifecycleEvents.SERVER_STARTED.register(SpatialLoop121FabricPlatform::setServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SpatialLoop121FabricPlatform.setServer(null));
    }
}
