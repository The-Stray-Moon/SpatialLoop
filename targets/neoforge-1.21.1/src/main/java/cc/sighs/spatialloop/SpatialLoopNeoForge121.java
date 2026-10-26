package cc.sighs.spatialloop;

import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 空间循环（SpatialLoop）。
 *
 * <p>唯一的注册项是循环世界生成器的 codec：生成器本身随 level.dat 保存，因此「空间循环区块半径」
 * 是每个存档自己的属性，换客户端、换配置文件都不会改变已经生成的世界。
 */
@Mod(SpatialLoopNeoForge121.MOD_ID)
public final class SpatialLoopNeoForge121 {
    public static final String MOD_ID = "spatialloop";

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<SpatialLoopChunkGenerator>> PERIODIC_GENERATOR =
            CHUNK_GENERATORS.register("periodic", () -> SpatialLoopChunkGenerator.CODEC);

    public SpatialLoopNeoForge121(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
    }
}
