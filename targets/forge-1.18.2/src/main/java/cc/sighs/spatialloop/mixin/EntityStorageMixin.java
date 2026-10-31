package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopLevel;
import it.unimi.dsi.fastutil.longs.LongSet;
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
 * 1.18.2 的实体存档折回：在 {@code EntityStorage} 调用 {@code IOWorker} 的读写点统一落到
 * canonical 区块坐标，并按偏移平移实体 NBT（{@code Position}/{@code Pos}）。
 *
 * <p>POI 走 {@link SectionStorageMixin}；区块本体由 {@link ChunkStorageMixin} 处理。
 * 1.18.2 的 {@code loadAsync} 是 protected，调用走 {@link IOWorkerAccessor}。
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

    /** {@code emptyChunks} 以区块坐标记录「这一列没有实体」，必须按 canonical 坐标判断。 */
    @Redirect(
            method = "loadEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/longs/LongSet;contains(J)Z"
            )
    )
    private boolean spatialloop$containsCanonical(LongSet set, long key) {
        int radius = LoopLevel.radiusOf(this.level);
        if (radius <= 0) {
            return set.contains(key);
        }
        long canonical = wrap(ChunkPos.getX(key), ChunkPos.getZ(key), radius).toLong();
        return set.contains(canonical);
    }

    @Redirect(
            method = "loadEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;loadAsync(Lnet/minecraft/world/level/ChunkPos;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<CompoundTag> spatialloop$loadCanonical(IOWorker worker, ChunkPos pos) {
        int radius = LoopLevel.radiusOf(this.level);
        if (radius <= 0) {
            return ((IOWorkerAccessor) worker).spatialloop$loadAsync(pos);
        }
        ChunkPos canonical = wrap(pos.x, pos.z, radius);
        if (canonical.equals(pos)) {
            return ((IOWorkerAccessor) worker).spatialloop$loadAsync(pos);
        }
        return ((IOWorkerAccessor) worker).spatialloop$loadAsync(canonical).thenApply(tag -> {
            if (tag == null) {
                return null;
            }
            CompoundTag copy = tag.copy();
            LoopChunkNbt.rebaseEntities(copy, pos.x - canonical.x, pos.z - canonical.z);
            return copy;
        });
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
        ChunkPos canonical = wrap(pos.x, pos.z, radius);
        if (tag != null) {
            this.emptyChunks.remove(canonical.toLong());
        }
        CompoundTag copy = tag == null ? null : tag.copy();
        if (copy != null) {
            LoopChunkNbt.rebaseEntities(copy, canonical.x - pos.x, canonical.z - pos.z);
        }
        return worker.store(canonical, copy);
    }

    private static ChunkPos wrap(int x, int z, int radius) {
        return new ChunkPos(LoopGeometry.wrapChunk(x, radius), LoopGeometry.wrapChunk(z, radius));
    }
}
