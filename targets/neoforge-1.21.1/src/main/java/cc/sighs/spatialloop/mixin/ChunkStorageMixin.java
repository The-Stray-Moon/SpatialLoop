package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 区块存档坐标折回：读写磁盘时把区块坐标按周期取模，落到同一个区域槽位。
 *
 * <p>为什么只折存档、不折内存里的区块：内存里的区块如果共享实例，方块实体、实体、区块包都需要
 * 按玩家做坐标翻译；只折存档则保留原版所有坐标语义（方块实体位置、实体位置、发包位置都是玩家
 * 看到的绝对坐标），只需要在 NBT 进出磁盘时把绝对坐标一起平移。
 *
 * <p>效果：区域文件永远只覆盖折回后的 {@code 2r × 2r} 个区块；玩家在 C 处破坏的方块会随区块卸载
 * 写进 canonical 槽位，之后走到 C+P、C+2P 等副本时读到的是同一份数据。
 */
@Mixin(ChunkStorage.class)
public abstract class ChunkStorageMixin implements LoopStorage {
    @Unique
    private int spatialloop$chunkRadius;

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
            return;
        }
        cir.setReturnValue(this.worker.loadAsync(canonical).thenApply(optional -> optional.map(tag -> {
            // canonical 空间 → 请求坐标空间
            LoopChunkNbt.rebase(tag, pos.x - canonical.x, pos.z - canonical.z);
            return tag;
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

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void spatialloop$writeCanonical(
            ChunkPos pos,
            CompoundTag tag,
            CallbackInfoReturnable<CompletableFuture<Void>> cir
    ) {
        int radius = this.spatialloop$chunkRadius;
        if (radius <= 0) {
            return;
        }
        ChunkPos canonical = wrap(pos, radius);
        if (canonical.equals(pos)) {
            return;
        }
        if (tag == null) {
            // 删除副本时清掉 canonical 槽位：同一块空间只有一份数据。
            cir.setReturnValue(this.worker.store(canonical, null));
            return;
        }
        // 请求坐标空间 → canonical 空间（tag 是这次保存新生成的对象，可以原地平移）
        LoopChunkNbt.rebase(tag, canonical.x - pos.x, canonical.z - pos.z);
        // 不调用 handleLegacyStructureIndex：新世界没有 1.12 之前的结构索引数据。
        cir.setReturnValue(this.worker.store(canonical, tag));
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
