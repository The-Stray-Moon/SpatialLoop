package cc.sighs.spatialloop;

import cc.sighs.spatialloop.client.SpatialLoopCurveConfig;
import cc.sighs.spatialloop.network.SpatialLoopNetwork;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 空间循环（SpatialLoop）。
 *
 * <p>生成器的 codec 随 {@code level.dat} 保存，所以「空间循环区块半径」是存档属性；
 * 客户端的球面弯折参数是独立的纯客户端配置（{@code config/spatialloop-client.toml}），
 * 自适应模式通过 {@link SpatialLoopNetwork} 拿到当前维度的循环半径。
 */
@Mod(SpatialLoopNeoForge121.MOD_ID)
public final class SpatialLoopNeoForge121 {
    public static final String MOD_ID = "spatialloop";

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<SpatialLoopChunkGenerator>> PERIODIC_GENERATOR =
            CHUNK_GENERATORS.register("periodic", () -> SpatialLoopChunkGenerator.CODEC);

    public SpatialLoopNeoForge121(IEventBus modEventBus, ModContainer modContainer) {
        CHUNK_GENERATORS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, SpatialLoopCurveConfig.SPEC);
        SpatialLoopNetwork.register(modEventBus);
    }
}
