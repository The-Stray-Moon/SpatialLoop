package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopBiomeManager;
import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopRadiusRegistry;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import java.util.List;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code ServerLevel} 构造结束时把生成器里的循环半径记到实例和 {@link LoopRadiusRegistry}。
 *
 * <p>Mixin 0.8.5 不支持在构造器 HEAD/FIELD 处注入，因此这里只在 RETURN 落地。构造器内部
 * {@code ChunkMap}/{@code ServerChunkCache} 需要半径时，直接从各自的 {@code ChunkGenerator}
 * 判断（见 {@code ChunkMapMixin}/{@code ServerChunkCacheMixin}），不依赖本字段。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements LoopLevel {
    @Unique
    private int spatialloop$loopRadius;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void spatialloop$captureLoopRadius(
            MinecraftServer server,
            Executor executor,
            LevelStorageSource.LevelStorageAccess storageAccess,
            ServerLevelData levelData,
            ResourceKey<Level> dimension,
            LevelStem stem,
            ChunkProgressListener progressListener,
            boolean debug,
            long seed,
            List<CustomSpawner> customSpawners,
            boolean tickTime,
            RandomSequences randomSequences,
            CallbackInfo ci
    ) {
        if (stem.generator() instanceof SpatialLoopChunkGenerator generator) {
            this.spatialloop$loopRadius = generator.radius();
        }
        LoopRadiusRegistry.put(dimension, this.spatialloop$loopRadius);
        if (this.spatialloop$loopRadius > 0) {
            // 生物群系的模糊查找也要按周期折回，否则 feature 的 BiomeFilter / 地表规则在群系边界会差一格。
            ((LoopBiomeManager) (Object) ((ServerLevel) (Object) this).getBiomeManager())
                    .spatialloop$periodBlocks(LoopGeometry.periodBlocks(this.spatialloop$loopRadius));
        }
    }

    @Override
    public int spatialloop$loopRadius() {
        return this.spatialloop$loopRadius;
    }
}
