package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopLevel;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.18.2 的 POI 存档折回：{@code SectionStorage} 的读列/写列都经过 {@code IOWorker}，
 * 在调用点落到 canonical 坐标并平移 {@code Sections.<Y>.Records[].pos}。
 *
 * <p>1.18.2 的 {@code tryRead} 用同步的 {@code IOWorker#load}（会抛 {@code IOException}），
 * {@code writeColumn} 用 {@code IOWorker#store}。
 * 只有 POI 的 {@code SectionStorage} 绑定了 {@code ServerLevel}（作为 {@code LevelHeightAccessor}）；
 * 不是 {@link Level} 的存储保持原版行为。
 */
@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin {
    @Shadow
    @Final
    private IOWorker worker;

    @Shadow
    @Final
    protected LevelHeightAccessor levelHeightAccessor;

    @Redirect(
            method = "tryRead",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;load(Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/nbt/CompoundTag;"
            )
    )
    private CompoundTag spatialloop$loadCanonical(IOWorker worker, ChunkPos pos) throws IOException {
        int radius = spatialloop$radius();
        if (radius <= 0) {
            return worker.load(pos);
        }
        ChunkPos canonical = wrap(pos, radius);
        CompoundTag tag = worker.load(canonical);
        if (tag == null || canonical.equals(pos)) {
            return tag;
        }
        CompoundTag copy = tag.copy();
        LoopChunkNbt.rebasePoi(copy, pos.x - canonical.x, pos.z - canonical.z);
        return copy;
    }

    @Redirect(
            // 只匹配 void 版本；另一个 writeColumn(ChunkPos, DynamicOps) 不调用 IOWorker。
            method = "writeColumn(Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;store(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private CompletableFuture<Void> spatialloop$storeCanonical(IOWorker worker, ChunkPos pos, CompoundTag tag) {
        int radius = spatialloop$radius();
        if (radius <= 0) {
            return worker.store(pos, tag);
        }
        ChunkPos canonical = wrap(pos, radius);
        CompoundTag copy = tag == null ? null : tag.copy();
        if (copy != null) {
            LoopChunkNbt.rebasePoi(copy, canonical.x - pos.x, canonical.z - pos.z);
        }
        return worker.store(canonical, copy);
    }

    private int spatialloop$radius() {
        return this.levelHeightAccessor instanceof Level level ? LoopLevel.radiusOf(level) : 0;
    }

    private static ChunkPos wrap(ChunkPos pos, int radius) {
        return new ChunkPos(
                LoopGeometry.wrapChunk(pos.x, radius),
                LoopGeometry.wrapChunk(pos.z, radius)
        );
    }
}
