package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopLevel;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.19.2 的 POI 存档折回：{@code SectionStorage} 的读列/写列都经过 {@code IOWorker}，
 * 在调用点落到 canonical 坐标并平移 {@code Sections.<Y>.Records[].pos}。
 *
 * <p>只有 POI 的 {@code SectionStorage} 绑定了 {@code ServerLevel}（作为 {@code LevelHeightAccessor}）；
 * 不是 {@link Level} 的存储保持原版行为。
 */
@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin {
    @Shadow
    @Final
    private IOWorker worker;

    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Redirect(
            method = "tryRead",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;loadAsync(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Optional<CompoundTag>> spatialloop$loadCanonical(IOWorker worker, ChunkPos pos) {
        int radius = spatialloop$radius();
        if (radius <= 0) {
            return worker.loadAsync(pos);
        }
        ChunkPos canonical = wrap(pos, radius);
        if (canonical.equals(pos)) {
            return worker.loadAsync(pos);
        }
        return worker.loadAsync(canonical).thenApply(optional -> optional.map(tag -> {
            CompoundTag copy = tag.copy();
            LoopChunkNbt.rebasePoi(copy, pos.x - canonical.x, pos.z - canonical.z);
            return copy;
        }));
    }

    @Redirect(
            method = "writeColumn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;store(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Void> spatialloop$storeCanonical(IOWorker worker, ChunkPos pos, CompoundTag tag) {
        int radius = spatialloop$radius();
        if (radius <= 0) {
            return worker.store(pos, tag);
        }
        ChunkPos canonical = wrap(pos, radius);
        CompoundTag copy = tag == null ? null : tag.copy();
        if (copy != null) {
            LoopChunkNbt.rebasePoi(copy, canonical.x - pos.x, canonical.z - pos.z);
        }
        return worker.store(canonical, copy);
    }

    private int spatialloop$radius() {
        return this.levelHeightAccessor instanceof Level level ? LoopLevel.radiusOf(level) : 0;
    }

    private static ChunkPos wrap(ChunkPos pos, int radius) {
        return new ChunkPos(
                LoopGeometry.wrapChunk(pos.x, radius),
                LoopGeometry.wrapChunk(pos.z, radius)
        );
    }
}
