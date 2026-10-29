package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.ownership.LoopChunkOwnership;
import cc.sighs.spatialloop.world.LoopChunkOwner;
import cc.sighs.spatialloop.world.LoopGenerationAdmission;
import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopPendingGeneration;
import cc.sighs.spatialloop.world.LoopLoadingLimits;
import cc.sighs.spatialloop.world.LoopGenerationReach;
import cc.sighs.spatialloop.world.LoopPoiRetirement;
import cc.sighs.spatialloop.world.LoopRetirementAccess;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 票据可创建空 holder，但生成任务取得依赖引用之前必须整圈准入。 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapOwnershipMixin implements LoopGenerationAdmission {
    private static final Logger LOGGER = LoggerFactory.getLogger("SpatialLoop");

    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    @Shadow @Final private LongSet toDrop;
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private PoiManager poiManager;
    @Shadow @Final private BlockableEventLoop<Runnable> mainThreadExecutor;
    @Shadow private void processUnloads(BooleanSupplier time) { }

    @Unique private final LoopChunkOwnership spatialloop$ownership = new LoopChunkOwnership();
    @Unique private final Map<Long, LoopChunkOwner> spatialloop$owners = new LinkedHashMap<>();
    /** 已观察到卸载、正在等实体/写入屏障的 owner；pump 只扫这一小集合，不扫全部 owner。 */
    @Unique private final Map<Long, LoopChunkOwner> spatialloop$retiring = new LinkedHashMap<>();
    @Unique private final Map<GenerationChunkHolder, LoopPendingGeneration> spatialloop$pending = new LinkedHashMap<>();
    @Unique private long spatialloop$nextPendingAttempt;
    @Unique private boolean spatialloop$pumping;
    @Unique private boolean spatialloop$pausedForNoSave;
    @Unique private volatile Throwable spatialloop$failure;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void spatialloop$validateRadius(CallbackInfo ci) {
        int radius = LoopLevel.radiusOf(this.level);
        LoopLoadingLimits.validateRadius(radius);
        if (radius <= 0) {
            return;
        }
        // 视距公式钉死了目标版本的原版生成依赖半径；版本升级后这里先失败，避免静默算错。
        int fullReach = LoopGenerationReach.forStatus(ChunkStatus.FULL);
        if (fullReach != LoopLoadingLimits.pinnedGenerationRadius()) {
            throw new IllegalStateException("SpatialLoop view distance math is pinned to FULL reach "
                    + LoopLoadingLimits.pinnedGenerationRadius() + " but this game reports " + fullReach
                    + "; update LoopLoadingLimits before opening a loop world.");
        }
        LOGGER.info(
                "SpatialLoop {}: radius={} chunks, period={} blocks, generation halo={}, "
                        + "max view/simulation distance={} chunks",
                this.level.dimension().location(),
                radius,
                LoopGeometry.periodBlocks(radius),
                LoopLoadingLimits.loadedHaloRadius(),
                LoopLoadingLimits.maxDistance(radius)
        );
    }

    @Override
    public boolean spatialloop$admit(GenerationChunkHolder holder, ChunkStatus status) {
        int radius = LoopLevel.radiusOf(this.level);
        if (radius <= 0) {
            return true;
        }
        spatialloop$checkThreadAndFailure();
        int ownershipReach = LoopGenerationReach.forStatus(status);
        if (ownershipReach >= radius) {
            throw new IllegalStateException("SpatialLoop generation range overlaps itself: radius=" + radius + ", reach=" + ownershipReach);
        }
        List<LoopChunkOwnership.Request> requests = new ArrayList<>(LoopGenerationReach.squareDependencyCount(ownershipReach));
        ChunkPos center = holder.getPos();
        for (int x = center.x - ownershipReach; x <= center.x + ownershipReach; x++) {
            for (int z = center.z - ownershipReach; z <= center.z + ownershipReach; z++) {
                long absolute = ChunkPos.asLong(x, z);
                long canonical = ChunkPos.asLong(LoopGeometry.wrapChunk(x, radius), LoopGeometry.wrapChunk(z, radius));
                ChunkHolder dependency = this.updatingChunkMap.get(absolute);
                LoopChunkOwner old = this.spatialloop$owners.get(canonical);
                if (old != null && old.holder() != dependency) {
                    // managedBlock 不经过下一 tick 的 purgeStaleTickets。仅提前退休已完成同步读
                    // 留下的 UNKNOWN 保温票据；仍在等待的请求和 PLAYER/FORCED 等真实需求不动。
                    ((cc.sighs.spatialloop.world.LoopTransientTickets)
                            ((ChunkMap) (Object) this).getDistanceManager())
                            .retireCompletedRequests(old.holder().getPos());
                    return spatialloop$defer(holder, status);
                }
                if (dependency == null || !ChunkLevel.isLoaded(dependency.getTicketLevel())) {
                    return spatialloop$defer(holder, status);
                }
                requests.add(new LoopChunkOwnership.Request(canonical, absolute));
            }
        }
        var admitted = this.spatialloop$ownership.claimRange(requests);
        if (admitted.isEmpty()) {
            return spatialloop$defer(holder, status);
        }
        LoopChunkOwnership.Claim claim = admitted.get();
        for (LoopChunkOwnership.Token token : claim.tokens()) {
            this.spatialloop$owners.computeIfAbsent(token.canonical(), key ->
                    new LoopChunkOwner(token, this.updatingChunkMap.get(token.absolute())));
        }
        // owner 的生命周期由原版 holder 卸载确认控制，不以生成任务结束作为释放条件。
        this.spatialloop$ownership.releaseDemand(claim);
        this.spatialloop$pending.remove(holder);
        return true;
    }

    @Unique
    private boolean spatialloop$defer(GenerationChunkHolder holder, ChunkStatus status) {
        LoopPendingGeneration previous = this.spatialloop$pending.get(holder);
        ChunkStatus requested = previous != null && previous.status().isAfter(status) ? previous.status() : status;
        this.spatialloop$pending.put(holder,
                new LoopPendingGeneration(requested, previous == null ? System.nanoTime() : previous.since()));
        return false;
    }

    @Inject(method = "runGenerationTasks", at = @At("HEAD"))
    private void spatialloop$pumpBeforeGeneration(CallbackInfo ci) {
        spatialloop$pumpHandoffs();
    }

    @Override
    public void spatialloop$pumpHandoffs() {
        if (LoopLevel.radiusOf(this.level) <= 0 || this.spatialloop$pumping) {
            return;
        }
        spatialloop$checkThreadAndFailure();
        this.spatialloop$pumping = true;
        try {
            // 原版 noSave 阶段本身不会 processUnloads；暂停换主与计时，避免关闭流程中误报永久冲突。
            if (this.level.noSave()) {
                this.spatialloop$pausedForNoSave = true;
                return;
            }
            if (this.spatialloop$pausedForNoSave) {
                long now = System.nanoTime();
                for (var entry : List.copyOf(this.spatialloop$pending.entrySet())) {
                    LoopPendingGeneration pending = entry.getValue();
                    this.spatialloop$pending.put(entry.getKey(),
                            new LoopPendingGeneration(pending.status(), now));
                }
                this.spatialloop$pausedForNoSave = false;
            }
            // runGenerationTasks 会被 ServerChunkCache.MainThreadExecutor.pollTask 在 managedBlock
            // 忙等循环里反复调用（同步 getChunk 每秒可达上千次），所以这里只能做 O(待退休) 的小工作，
            // 不能每次扫全部 owner 或重试全部 pending。
            boolean unloadWork = !this.toDrop.isEmpty() || !this.pendingUnloads.isEmpty();
            boolean retireWork = !this.spatialloop$retiring.isEmpty();
            LoopRetirementAccess entities = null;
            if (unloadWork || retireWork) {
                // 不伪造票据、不强删 holder；原版降级会取消任务并归还 generation 引用。
                if (unloadWork) {
                    this.processUnloads(() -> true);
                }
                entities = (LoopRetirementAccess) this.level;
                entities.spatialloop$pumpEntities();
                spatialloop$checkThreadAndFailure();
            }
            if (retireWork) {
                for (LoopChunkOwner owner : List.copyOf(this.spatialloop$retiring.values())) {
                    long canonical = owner.token().canonical();
                    var snapshot = this.spatialloop$ownership.snapshot(canonical);
                    if (snapshot.isEmpty()) {
                        // flush 成功后协调器已释放 canonical；这里把两个索引一起清掉。
                        this.spatialloop$owners.remove(canonical, owner);
                        this.spatialloop$retiring.remove(canonical, owner);
                        continue;
                    }
                    LoopChunkOwnership.State state = snapshot.get().state();
                    if (state == LoopChunkOwnership.State.FAILED) {
                        throw new IllegalStateException("SpatialLoop refused handoff after storage failure at "
                                + owner.holder().getPos(), snapshot.get().failure());
                    }
                    if (state == LoopChunkOwnership.State.ACTIVE) {
                        if (!this.spatialloop$ownership.beginRetirement(owner.token())) {
                            continue;
                        }
                        state = LoopChunkOwnership.State.RETIRING;
                    }
                    if (state == LoopChunkOwnership.State.RETIRING
                            && entities.spatialloop$entitiesRetired(owner.holder().getPos())) {
                        ChunkPos pos = owner.holder().getPos();
                        CompletableFuture<Void> poi = ((LoopPoiRetirement) this.poiManager).spatialloop$retirePoi(pos);
                        CompletableFuture<Void> saved = CompletableFuture.allOf(
                                ((LoopStorageBarrier) this).spatialloop$writeBarrier(pos),
                                entities.spatialloop$entityWriteBarrier(pos), poi);
                        this.spatialloop$ownership.flush(owner.token(), saved);
                    }
                }
            }
            long now = System.nanoTime();
            if (!this.spatialloop$pending.isEmpty()
                    && now - this.spatialloop$nextPendingAttempt >= 20_000_000L) {
                this.spatialloop$nextPendingAttempt = now;
                for (var entry : List.copyOf(this.spatialloop$pending.entrySet())) {
                    GenerationChunkHolder holder = entry.getKey();
                    LoopPendingGeneration pending = entry.getValue();
                    ChunkStatus allowed = ChunkLevel.generationStatus(holder.getTicketLevel());
                    if (this.updatingChunkMap.get(holder.getPos().toLong()) != holder || allowed == null
                            || pending.status().isAfter(allowed)) {
                        this.spatialloop$pending.remove(holder);
                        continue;
                    }
                    if (spatialloop$admit(holder, pending.status())) {
                        holder.scheduleChunkGenerationTask(pending.status(), (ChunkMap) (Object) this);
                    } else if (now - pending.since() > 120_000_000_000L) {
                        throw new IllegalStateException("SpatialLoop handoff blocked for 120 seconds at " + holder.getPos()
                                + " requesting " + pending.status() + ": " + spatialloop$blockedReason(holder, pending.status())
                                + "; overlapping active tickets, multiple distant players, forced chunks or reentrant chunk loading are unsupported. "
                                + "No old chunk was discarded. Reduce the active range or restore a backup; the saved period was not changed.");
                    }
                }
            }
        } finally {
            this.spatialloop$pumping = false;
        }
    }

    @Redirect(method = "scheduleUnload", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;thenRunAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> spatialloop$observeUnload(CompletableFuture<?> ready, Runnable unload,
            Executor executor, long position, ChunkHolder holder) {
        return ready.thenRunAsync(() -> {
            try {
                unload.run();
                int radius = LoopLevel.radiusOf(this.level);
                if (radius > 0 && this.updatingChunkMap.get(position) != holder && this.pendingUnloads.get(position) != holder) {
                    ChunkPos pos = holder.getPos();
                    long canonical = ChunkPos.asLong(LoopGeometry.wrapChunk(pos.x, radius), LoopGeometry.wrapChunk(pos.z, radius));
                    LoopChunkOwner owner = this.spatialloop$owners.get(canonical);
                    if (owner != null && owner.holder() == holder) {
                        owner.markUnloaded();
                        this.spatialloop$retiring.putIfAbsent(canonical, owner);
                    }
                }
            } catch (Throwable failure) {
                this.spatialloop$failure = failure;
                throw failure;
            }
        }, executor);
    }

    @Inject(method = "acquireGeneration", at = @At("HEAD"))
    private void spatialloop$assertAdmission(long position, CallbackInfoReturnable<GenerationChunkHolder> cir) {
        int radius = LoopLevel.radiusOf(this.level);
        if (radius > 0) {
            ChunkPos pos = new ChunkPos(position);
            long key = ChunkPos.asLong(LoopGeometry.wrapChunk(pos.x, radius), LoopGeometry.wrapChunk(pos.z, radius));
            LoopChunkOwner owner = this.spatialloop$owners.get(key);
            if (owner == null || owner.token().absolute() != position || owner.unloaded()
                    || owner.holder() != this.updatingChunkMap.get(position)) {
                throw new IllegalStateException("SpatialLoop generation bypassed range admission at " + pos);
            }
        }
    }

    @Redirect(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;reportChunkSaveFailure(Ljava/lang/Throwable;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;)V"))
    private void spatialloop$rememberSerializationFailure(MinecraftServer server, Throwable failure,
            RegionStorageInfo info, ChunkPos pos) {
        if (LoopLevel.radiusOf(this.level) > 0) {
            this.spatialloop$failure = failure;
        }
        server.reportChunkSaveFailure(failure, info, pos);
    }

    @Inject(method = "handleChunkLoadFailure", at = @At("HEAD"))
    private void spatialloop$doNotGenerateOverFailedRead(Throwable failure, ChunkPos pos,
            CallbackInfoReturnable<ChunkAccess> cir) {
        if (LoopLevel.radiusOf(this.level) > 0) {
            this.spatialloop$failure = failure;
            throw new IllegalStateException("SpatialLoop refuses to replace unreadable saved chunk " + pos, failure);
        }
    }

    @Unique
    private String spatialloop$blockedReason(GenerationChunkHolder holder, ChunkStatus status) {
        int radius = LoopLevel.radiusOf(this.level);
        int reach = LoopGenerationReach.forStatus(status);
        ChunkPos center = holder.getPos();
        for (int x = center.x - reach; x <= center.x + reach; x++) {
            for (int z = center.z - reach; z <= center.z + reach; z++) {
                ChunkHolder dependency = this.updatingChunkMap.get(ChunkPos.asLong(x, z));
                if (dependency == null || !ChunkLevel.isLoaded(dependency.getTicketLevel())) {
                    return "missing dependency " + new ChunkPos(x, z) + " ticket="
                            + (dependency == null ? "none" : dependency.getTicketLevel());
                }
                long key = ChunkPos.asLong(LoopGeometry.wrapChunk(x, radius), LoopGeometry.wrapChunk(z, radius));
                LoopChunkOwner old = this.spatialloop$owners.get(key);
                if (old != null && (old.holder() != dependency || old.unloaded())) {
                    return "owner " + old.holder().getPos() + " ticket=" + old.holder().getTicketLevel()
                            + " refs=" + old.holder().getGenerationRefCount() + " unloaded=" + old.unloaded()
                            + " state=" + this.spatialloop$ownership.snapshot(key) + " blocks " + new ChunkPos(x, z);
                }
            }
        }
        return "range owner is retiring or flushing";
    }

    @Unique
    private void spatialloop$checkThreadAndFailure() {
        if (!this.mainThreadExecutor.isSameThread()) {
            throw new IllegalStateException("SpatialLoop handoff must run on the server main thread");
        }
        if (this.spatialloop$failure != null) {
            throw new IllegalStateException("SpatialLoop handoff stopped after load/save/unload failure", this.spatialloop$failure);
        }
    }
}
