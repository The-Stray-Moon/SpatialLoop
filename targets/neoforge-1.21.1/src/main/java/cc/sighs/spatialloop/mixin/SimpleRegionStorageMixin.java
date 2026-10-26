package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopRadiusRegistry;
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
public abstract class SimpleRegionStorageMixin {
    @Shadow
    @Final
    private IOWorker worker;

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
        if (canonical.equals(pos)) {
            return;
        }
        cir.setReturnValue(this.worker.loadAsync(canonical).thenApply(optional -> optional.map(tag -> {
            rebase(type, tag, pos.x - canonical.x, pos.z - canonical.z);
            return tag;
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
        if (canonical.equals(pos)) {
            return;
        }
        if (tag == null) {
            cir.setReturnValue(this.worker.store(canonical, null));
            return;
        }
        rebase(type, tag, canonical.x - pos.x, canonical.z - pos.z);
        cir.setReturnValue(this.worker.store(canonical, tag));
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
