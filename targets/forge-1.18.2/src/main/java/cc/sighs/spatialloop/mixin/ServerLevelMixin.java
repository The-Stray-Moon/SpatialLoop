package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopBiomeManager;
import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopRadiusRegistry;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code ServerLevel} 构造结束时把生成器里的循环半径记到实例和 {@link LoopRadiusRegistry}。
 *
 * <p>Mixin 0.8.5 不支持在构造器 HEAD/FIELD 处注入，因此这里只在 RETURN 落地；构造器内部
 * {@code ChunkMap}/{@code ServerChunkCache} 需要半径时，直接从各自的 {@code ChunkGenerator}
 * 判断（见 {@code ChunkMapMixin}/{@code ServerChunkCacheMixin}），不依赖本字段。
 *
 * <p>1.18.2 的构造器参数是 {@code (..., Holder<DimensionType>, ChunkProgressListener, ChunkGenerator, ...)}，
 * 没有 {@code RandomSequences}/{@code LevelStem}；这里只取 {@code CallbackInfo}，生成器从
 * {@code ServerChunkCache} 读，避免绑定具体参数列表。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements LoopLevel {
    @Unique
    private int spatialloop$loopRadius;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void spatialloop$captureLoopRadius(CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        ChunkGenerator generator = self.getChunkSource().getGenerator();
        if (generator instanceof SpatialLoopChunkGenerator spatialLoopGenerator) {
            this.spatialloop$loopRadius = spatialLoopGenerator.radius();
        }
        LoopRadiusRegistry.put(self.dimension(), this.spatialloop$loopRadius);
        if (this.spatialloop$loopRadius > 0) {
            // 生物群系的模糊查找也要按周期折回，否则 feature 的 BiomeFilter / 地表规则在群系边界会差一格。
            ((LoopBiomeManager) (Object) self.getBiomeManager())
                    .spatialloop$periodBlocks(LoopGeometry.periodBlocks(this.spatialloop$loopRadius));
        }
    }

    @Override
    public int spatialloop$loopRadius() {
        return this.spatialloop$loopRadius;
    }
}
