package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopRetirementAccess;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerRetirementMixin<T extends EntityAccess>
        implements LoopRetirementAccess {
    @Shadow @Final private EntityPersistentStorage<T> permanentStorage;
    @Shadow @Final private Long2ObjectMap<Visibility> chunkVisibility;
    // ChunkLoadStatus 是 package-private；只检查 key，不依赖枚举常量。
    @Shadow @Final private Long2ObjectMap<?> chunkLoadStatuses;
    @Shadow @Final private LongSet chunksToUnload;
    @Shadow @Final private Queue<ChunkEntities<T>> loadingInbox;
    @Shadow @Final private EntitySectionStorage<T> sectionStorage;
    @Shadow public abstract void tick();
    @Unique private final Map<Long, Throwable> spatialloop$loadFailures = new ConcurrentHashMap<>();

    @Override
    public void spatialloop$pumpEntities() {
        this.tick();
    }

    @Override
    public boolean spatialloop$entitiesRetired(ChunkPos pos) {
        long key = pos.toLong();
        Throwable failure = this.spatialloop$loadFailures.get(key);
        if (failure != null) {
            throw new IllegalStateException("SpatialLoop refuses entity handoff after failed read at " + pos, failure);
        }
        return !this.chunkLoadStatuses.containsKey(key)
                && !this.chunksToUnload.contains(key)
                && this.chunkVisibility.get(key) == Visibility.HIDDEN
                && this.sectionStorage.getExistingSectionPositionsInChunk(key).findAny().isEmpty()
                && this.loadingInbox.stream().noneMatch(chunk -> chunk.getPos().equals(pos));
    }

    @Override
    public CompletableFuture<Void> spatialloop$entityWriteBarrier(ChunkPos pos) {
        if (!this.spatialloop$entitiesRetired(pos)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Entities are not retired: " + pos));
        }
        return ((LoopStorageBarrier) this.permanentStorage).spatialloop$writeBarrier(pos);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
            method = "requestChunkLoad",
            at = @org.spongepowered.asm.mixin.injection.At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/CompletableFuture;exceptionally(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private <E> CompletableFuture<E> spatialloop$recordEntityLoadFailure(
            CompletableFuture<E> future, java.util.function.Function<Throwable, ? extends E> fallback, long chunkPos) {
        return future.exceptionally(failure -> {
            this.spatialloop$loadFailures.put(chunkPos, failure);
            return fallback.apply(failure);
        });
    }
}
