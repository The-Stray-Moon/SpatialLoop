package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopLevel;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.20.1 的实体存档折回：在 {@code EntityStorage} 调用 {@code IOWorker} 的读写点统一落到
 * canonical 区块坐标，并按偏移平移实体 NBT（{@code Position}/{@code Pos}）。
 *
 * <p>POI 走 {@link SectionStorageMixin}；区块本体仍由 {@link ChunkStorageMixin} 处理。
 */
@Mixin(EntityStorage.class)
public abstract class EntityStorageMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private IOWorker worker;

    @Shadow
    @Final
    private LongSet emptyChunks;

    @Redirect(
            method = "loadEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;loadAsync(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Optional<CompoundTag>> spatialloop$loadCanonical(IOWorker worker, ChunkPos pos) {
        int radius = LoopLevel.radiusOf(this.level);
        if (radius <= 0) {
            return worker.loadAsync(pos);
        }
        ChunkPos canonical = wrap(pos, radius);
        if (canonical.equals(pos)) {
            return worker.loadAsync(pos);
        }
        return worker.loadAsync(canonical).thenApply(optional -> optional.map(tag -> {
            CompoundTag copy = tag.copy();
            LoopChunkNbt.rebaseEntities(copy, pos.x - canonical.x, pos.z - canonical.z);
            return copy;
        }));
    }

    @Redirect(
            method = "storeEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;store(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Void> spatialloop$storeCanonical(IOWorker worker, ChunkPos pos, CompoundTag tag) {
        int radius = LoopLevel.radiusOf(this.level);
        if (radius <= 0) {
            return worker.store(pos, tag);
        }
        ChunkPos canonical = wrap(pos, radius);
        if (tag != null) {
            this.emptyChunks.remove(canonical.toLong());
        }
        CompoundTag copy = tag == null ? null : tag.copy();
        if (copy != null) {
            LoopChunkNbt.rebaseEntities(copy, canonical.x - pos.x, canonical.z - pos.z);
        }
        return worker.store(canonical, copy);
    }

    private static ChunkPos wrap(ChunkPos pos, int radius) {
        return new ChunkPos(
                LoopGeometry.wrapChunk(pos.x, radius),
                LoopGeometry.wrapChunk(pos.z, radius)
        );
    }
}
