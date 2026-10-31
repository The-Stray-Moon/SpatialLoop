package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopRetirementAccess;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityPersistentStorage;
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
    @Shadow public abstract void tick();
    @Unique private final Map<Long, Throwable> spatialloop$loadFailures = new ConcurrentHashMap<>();

    @Override
    public void spatialloop$pumpEntities() {
        this.tick();
    }

    @Override
    public boolean spatialloop$entitiesRetired(ChunkPos pos) {
        long key = pos.pack();
        Throwable failure = this.spatialloop$loadFailures.get(key);
        if (failure != null) {
            throw new IllegalStateException("SpatialLoop refuses entity handoff after failed read at " + pos, failure);
        }
        // Vanilla 的「已卸载」只有三个可观察条件：
        //   * updateChunkStatus(HIDDEN) 会把 key 放进 chunksToUnload，且 chunkVisibility 移除条目
        //     （默认值是 HIDDEN，所以 get() 仍返回 HIDDEN）；
        //   * tick() 的 processChunkUnload -> storeChunkSections 保存实体、移除 chunkLoadStatuses，
        //     并把实体从 level 里摘掉。
        // EntitySectionStorage 里的空 section 容器不会随卸载删除（vanilla 也不删），所以不能用
        // “section 为空”判断实体已退役，否则玩家所在区块会永远等不到退役。
        // inbox 未消费时 chunkLoadStatuses 必定仍是 PENDING，已被第一项覆盖。
        return !this.chunkLoadStatuses.containsKey(key)
                && !this.chunksToUnload.contains(key)
                && this.chunkVisibility.get(key) == Visibility.HIDDEN;
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
