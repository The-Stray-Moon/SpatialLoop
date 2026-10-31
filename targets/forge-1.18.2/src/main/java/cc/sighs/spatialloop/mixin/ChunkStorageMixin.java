package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.ownership.LoopWriteBarrier;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopStorage;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import java.io.IOException;
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
 * <p>1.18.2 的 {@code ChunkStorage#read} 是<b>同步</b>的（{@code IOWorker#load}），{@code write}
 * 返回 {@code void}，也没有 1.19+ 的 {@code isOldChunkAround}。所以这里直接在 HEAD 取消并调用
 * canonical 坐标的读写，同时把真实 IO future 记进屏障。
 */
@Mixin(ChunkStorage.class)
public abstract class ChunkStorageMixin implements LoopStorage, LoopStorageBarrier {
    @Unique
    private final LoopWriteBarrier spatialloop$writes = new LoopWriteBarrier();

    @Unique
    private int spatialloop$chunkRadius;

    @Shadow
    @Final
    private IOWorker worker;

    @Override
    public java.util.concurrent.CompletableFuture<Void> spatialloop$writeBarrier(ChunkPos pos) {
        int radius = this.spatialloop$chunkRadius;
        return this.spatialloop$writes.snapshot((radius > 0 ? wrap(pos, radius) : pos).toLong());
    }

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void spatialloop$readCanonical(
            ChunkPos pos,
            CallbackInfoReturnable<CompoundTag> cir
    ) throws IOException {
        int radius = this.spatialloop$chunkRadius;
        if (radius <= 0) {
            return;
        }
        ChunkPos canonical = wrap(pos, radius);
        if (canonical.equals(pos)) {
            // canonical 位置无需平移，也不该为了一次读取深拷贝整块 NBT；交给原版读取。
            return;
        }
        CompoundTag tag = this.worker.load(canonical);
        if (tag != null) {
            CompoundTag copy = tag.copy();
            // canonical 空间 → 请求坐标空间；不要平移 IO 层持有的快照。
            LoopChunkNbt.rebase(copy, pos.x - canonical.x, pos.z - canonical.z);
            tag = copy;
        }
        cir.setReturnValue(tag);
    }

    /**
     * 1.18.2 的 {@code ChunkStorage#write} 返回 {@code void}（原版不把写 future 暴露给调用方），
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
