package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.ownership.LoopWriteBarrier;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopRadiusRegistry;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 实体与 POI 存档也折回同一个区域槽位。
 *
 * <p>{@code SimpleRegionStorage} 只被两个东西用：实体的 {@code EntityStorage}（type = {@code entities}）
 * 和 POI 的 {@code SectionStorage}（type = {@code poi}）。区块本体走的是 {@code ChunkStorage}，不在
 * 这里处理。
 *
 * <p>两个格式的绝对坐标：
 * <ul>
 *   <li>实体：{@code Position}（区块坐标 int[2]）和每条实体的 {@code Pos}（世界坐标 double[3]）。</li>
 *   <li>POI：{@code Sections.<Y>.Records[].pos}（BlockPos 打包 long）。</li>
 * </ul>
 * 读写时按存档槽的偏移平移，副本读到的实体与 POI 就落在副本自己的坐标上。
 */
@Mixin(SimpleRegionStorage.class)
public abstract class SimpleRegionStorageMixin implements LoopStorageBarrier {
    @org.spongepowered.asm.mixin.Unique
    private final LoopWriteBarrier spatialloop$writes = new LoopWriteBarrier();

    @Shadow
    @Final
    private IOWorker worker;

    @Override
    public CompletableFuture<Void> spatialloop$writeBarrier(ChunkPos pos) {
        RegionStorageInfo info = ((SimpleRegionStorage) (Object) this).storageInfo();
        int radius = LoopRadiusRegistry.get(info.dimension());
        if (radius <= 0 || !isFoldable(info.type())) {
            return CompletableFuture.completedFuture(null);
        }
        return this.spatialloop$writes.snapshot(wrap(pos, radius).toLong());
    }

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void spatialloop$readCanonical(
            ChunkPos pos,
            CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir
    ) {
        RegionStorageInfo info = ((SimpleRegionStorage) (Object) this).storageInfo();
        String type = info.type();
        int radius = LoopRadiusRegistry.get(info.dimension());
        if (radius <= 0 || !isFoldable(type)) {
            return;
        }
        ChunkPos canonical = wrap(pos, radius);
        cir.setReturnValue(this.worker.loadAsync(canonical).thenApply(optional -> optional.map(tag -> {
            CompoundTag copy = tag.copy();
            rebase(type, copy, pos.x - canonical.x, pos.z - canonical.z);
            return copy;
        })));
    }

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void spatialloop$writeCanonical(
            ChunkPos pos,
            CompoundTag tag,
            CallbackInfoReturnable<CompletableFuture<Void>> cir
    ) {
        RegionStorageInfo info = ((SimpleRegionStorage) (Object) this).storageInfo();
        String type = info.type();
        int radius = LoopRadiusRegistry.get(info.dimension());
        if (radius <= 0 || !isFoldable(type)) {
            return;
        }
        ChunkPos canonical = wrap(pos, radius);
        cir.setReturnValue(this.spatialloop$writes.submit(canonical.toLong(), () -> {
            CompoundTag copy = tag == null ? null : tag.copy();
            if (copy != null) {
                rebase(type, copy, canonical.x - pos.x, canonical.z - pos.z);
            }
            // canonical 路径和删除也跟踪真实 IO；这里只观测，不等待或交接内存副本。
            return this.worker.store(canonical, copy);
        }));
    }

    private static boolean isFoldable(String type) {
        return "entities".equals(type) || "poi".equals(type);
    }

    private static void rebase(String type, CompoundTag tag, int deltaChunkX, int deltaChunkZ) {
        if ("entities".equals(type)) {
            LoopChunkNbt.rebaseEntities(tag, deltaChunkX, deltaChunkZ);
        } else {
            LoopChunkNbt.rebasePoi(tag, deltaChunkX, deltaChunkZ);
        }
    }

    private static ChunkPos wrap(ChunkPos pos, int radius) {
        return new ChunkPos(LoopGeometry.wrapChunk(pos.x, radius), LoopGeometry.wrapChunk(pos.z, radius));
    }
}
