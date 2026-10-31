package cc.sighs.spatialloop;

import cc.sighs.spatialloop.client.SpatialLoopForgeCurveConfig;
import cc.sighs.spatialloop.network.SpatialLoopForgeNetwork;
import cc.sighs.spatialloop.spi.SpatialLoopServices;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge 1.19.2 入口：加载器注册与 SPI 安装，世界逻辑与其它 target 共享。
 *
 * <p>1.19.2 还没有 {@code net.minecraft.core.registries.Registries}，注册表键直接用
 * {@link Registry} 上的静态字段。
 */
@Mod(SpatialLoop.MOD_ID)
public final class SpatialLoopForge {
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registry.CHUNK_GENERATOR_REGISTRY, SpatialLoop.MOD_ID);

    public SpatialLoopForge() {
        SpatialLoopServices.install(SpatialLoop1192ForgePlatform.INSTANCE);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        CHUNK_GENERATORS.register("periodic", () -> SpatialLoopChunkGenerator.CODEC);
        CHUNK_GENERATORS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SpatialLoopForgeCurveConfig.SPEC);
        SpatialLoopForgeNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(SpatialLoopForgeNetwork::onLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(SpatialLoopForgeNetwork::onChangedDimension);
    }
}
