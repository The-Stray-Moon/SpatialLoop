package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.ownership.LoopWriteBarrier;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopStorage;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 区块存档坐标折回：读写磁盘时把区块坐标按周期取模，落到同一个区域槽位。
 *
 * <p>为什么只折存档、不折内存里的区块：内存里的区块如果共享实例，方块实体、实体、区块包都需要
 * 按玩家做坐标翻译；只折存档则保留原版所有坐标语义（方块实体位置、实体位置、发包位置都是玩家
 * 看到的绝对坐标），只需要在 NBT 进出磁盘时把绝对坐标一起平移。
 *
 * <p>效果：区域文件只覆盖折回后的 {@code 2r × 2r} 个区块，副本共用 canonical 槽位。
 * 已跟踪真实写入屏障，但尚未接入内存副本交接；预读快照及迟到保存仍可能导致绕圈时修改丢失。
 */
@Mixin(ChunkStorage.class)
public abstract class ChunkStorageMixin implements LoopStorage, LoopStorageBarrier {
    @Unique
    private final LoopWriteBarrier spatialloop$writes = new LoopWriteBarrier();

    @Unique
    private int spatialloop$chunkRadius;

    @Override
    public CompletableFuture<Void> spatialloop$writeBarrier(ChunkPos pos) {
        int radius = this.spatialloop$chunkRadius;
        return this.spatialloop$writes.snapshot((radius > 0 ? wrap(pos, radius) : pos).toLong());
    }

    @Shadow
    @Final
    private IOWorker worker;

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void spatialloop$readCanonical(
            ChunkPos pos,
            CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir
    ) {
        int radius = this.spatialloop$chunkRadius;
        if (radius <= 0) {
            return;
        }
        ChunkPos canonical = wrap(pos, radius);
        if (canonical.equals(pos)) {
            // canonical 位置无需平移，也不该为了一次读取深拷贝整块 NBT；交给原版读取。
            return;
        }
        cir.setReturnValue(this.worker.loadAsync(canonical).thenApply(optional -> optional.map(tag -> {
            CompoundTag copy = tag.copy();
            // canonical 空间 → 请求坐标空间；不要平移 IO 层持有的快照。
            LoopChunkNbt.rebase(copy, pos.x - canonical.x, pos.z - canonical.z);
            return copy;
        })));
    }

    /**
     * 循环世界一定是本模组新生成的，不存在需要 blending 的旧区块；直接跳过扫描。
     *
     * <p>不跳过的话，原版会用<b>未折回</b>的坐标去扫一整个 region（1024 个区块），还会在区域外创建
     * 一堆 0 字节的 {@code .mca} 文件。
     */
    @Inject(method = "isOldChunkAround", at = @At("HEAD"), cancellable = true)
    private void spatialloop$skipOldChunkScan(
            ChunkPos pos,
            int radius,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.spatialloop$chunkRadius > 0) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 1.20.1 的 {@code ChunkStorage#write} 返回 {@code void}（原版不把写 future 暴露给调用方），
     * 所以这里不能像新版那样用返回值交付 future：先按 canonical 坐标入队，把 {@code IOWorker#store}
     * 的真实 future 记进屏障，再取消原版写入，避免同一份数据又按绝对坐标写一遍。
     */
    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void spatialloop$writeCanonical(ChunkPos pos, CompoundTag tag, CallbackInfo ci) {
        int radius = this.spatialloop$chunkRadius;
        if (radius <= 0) {
            return;
        }
        ChunkPos canonical = wrap(pos, radius);
        if (canonical.equals(pos)) {
            // canonical 写入不需要平移，也就不需要深拷贝；仍要跟踪真实 IO future 供交接屏障使用。
            this.spatialloop$writes.submit(pos.toLong(), () -> this.worker.store(pos, tag));
        } else {
            CompoundTag copy = tag == null ? null : tag.copy();
            if (copy != null) {
                LoopChunkNbt.rebase(copy, canonical.x - pos.x, canonical.z - pos.z);
            }
            // canonical 路径和删除也必须覆盖；不以 exceptionally 恢复后的 future 作为屏障。
            // 循环新世界没有 1.12 之前的结构索引数据，原版 write 里的 legacy 结构索引不需要维护。
            this.spatialloop$writes.submit(canonical.toLong(), () -> this.worker.store(canonical, copy));
        }
        ci.cancel();
    }

    @Override
    public int spatialloop$chunkRadius() {
        return this.spatialloop$chunkRadius;
    }

    @Override
    public void spatialloop$setChunkRadius(int radius) {
        this.spatialloop$chunkRadius = radius;
    }

    private static ChunkPos wrap(ChunkPos pos, int radius) {
        return new ChunkPos(LoopGeometry.wrapChunk(pos.x, radius), LoopGeometry.wrapChunk(pos.z, radius));
    }
}
