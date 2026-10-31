package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.ownership.LoopWriteBarrier;
import cc.sighs.spatialloop.world.LoopChunkNbt;
import cc.sighs.spatialloop.world.LoopRadiusRegistry;
import cc.sighs.spatialloop.world.LoopStorage;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
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
 * <p>效果：区域文件只覆盖折回后的 {@code 2r × 2r} 个区块，副本共用 canonical 槽位。
 * 已跟踪真实写入屏障，但尚未接入内存副本交接；预读快照及迟到保存仍可能导致绕圈时修改丢失。
 *
 * <p>26.1 起区块不再走独立的 {@code ChunkStorage}：{@code ChunkMap} 直接继承
 * {@code SimpleRegionStorage}，chunk 存储的 {@code RegionStorageInfo.type()} 是 {@code "chunk"}。
 * 所以三类存储都在这里折回：chunk 用 {@code ChunkMap} 构造时写入的半径（{@link LoopStorage}），
 * entities / poi 用维度查表（{@link LoopRadiusRegistry}）。
 *
 * <p>两个格式的绝对坐标：
 * <ul>
 *   <li>区块：方块实体、计划刻、结构起点、实体与 POI 记录，见 {@link LoopChunkNbt#rebase}。</li>
 *   <li>实体：{@code Position}（区块坐标 int[2]）和每条实体的 {@code Pos}（世界坐标 double[3]）。</li>
 *   <li>POI：{@code Sections.<Y>.Records[].pos}（BlockPos 打包 long）。</li>
 * </ul>
 * 读写时按存档槽的偏移平移，副本读到的区块、实体与 POI 就落在副本自己的坐标上。
 */
@Mixin(SimpleRegionStorage.class)
public abstract class SimpleRegionStorageMixin implements LoopStorage, LoopStorageBarrier {
    @Unique
    private final LoopWriteBarrier spatialloop$writes = new LoopWriteBarrier();

    @Unique
    private int spatialloop$chunkRadius;

    @Shadow
    @Final
    private IOWorker worker;

    @Override
    public CompletableFuture<Void> spatialloop$writeBarrier(ChunkPos pos) {
        RegionStorageInfo info = ((SimpleRegionStorage) (Object) this).storageInfo();
        int radius = this.spatialloop$radius(info);
        if (radius <= 0 || !isFoldable(info.type())) {
            return CompletableFuture.completedFuture(null);
        }
        return this.spatialloop$writes.snapshot(wrap(pos, radius).pack());
    }

    @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    private void spatialloop$readCanonical(
            ChunkPos pos,
            CallbackInfoReturnable<CompletableFuture<Optional<CompoundTag>>> cir
    ) {
        RegionStorageInfo info = ((SimpleRegionStorage) (Object) this).storageInfo();
        String type = info.type();
        int radius = this.spatialloop$radius(info);
        if (radius <= 0 || !isFoldable(type)) {
            return;
        }
        ChunkPos canonical = wrap(pos, radius);
        if (canonical.equals(pos)) {
            // canonical 读取不需要平移；交给原版，避免深拷贝区块/实体/POI NBT。
            return;
        }
        cir.setReturnValue(this.worker.loadAsync(canonical).thenApply(optional -> optional.map(tag -> {
            CompoundTag copy = tag.copy();
            rebase(type, copy, pos.x() - canonical.x(), pos.z() - canonical.z());
            return copy;
        })));
    }

    /**
     * 折回后的写入。{@code write(ChunkPos, CompoundTag)} 也委托到这个方法，所以这里只接管
     * {@code Supplier} 重载即可覆盖区块、实体与 POI 的所有写入路径。
     */
    @Inject(
            method = "write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void spatialloop$writeCanonical(
            ChunkPos pos,
            Supplier<CompoundTag> supplier,
            CallbackInfoReturnable<CompletableFuture<Void>> cir
    ) {
        RegionStorageInfo info = ((SimpleRegionStorage) (Object) this).storageInfo();
        String type = info.type();
        int radius = this.spatialloop$radius(info);
        if (radius <= 0 || !isFoldable(type)) {
            return;
        }
        ChunkPos canonical = wrap(pos, radius);
        if (canonical.equals(pos)) {
            // canonical 写入不需要平移，也就不需要深拷贝；仍要跟踪真实 IO future 供交接屏障使用。
            cir.setReturnValue(this.spatialloop$writes.submit(canonical.pack(),
                    () -> this.worker.store(canonical, supplier)));
            return;
        }
        cir.setReturnValue(this.spatialloop$writes.submit(canonical.pack(), () -> this.worker.store(canonical, () -> {
            CompoundTag tag = supplier.get();
            if (tag == null) {
                return null;
            }
            CompoundTag copy = tag.copy();
            rebase(type, copy, canonical.x() - pos.x(), canonical.z() - pos.z());
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
            int range,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.spatialloop$chunkRadius > 0) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public int spatialloop$chunkRadius() {
        return this.spatialloop$chunkRadius;
    }

    @Override
    public void spatialloop$setChunkRadius(int radius) {
        this.spatialloop$chunkRadius = radius;
    }

    @Unique
    private int spatialloop$radius(RegionStorageInfo info) {
        // chunk 存储就是 ChunkMap 自己，半径在它的构造器里写入；实体/POI 的存储没有维度生成器，
        // 只能按 RegionStorageInfo 的维度键查表。
        return "chunk".equals(info.type()) ? this.spatialloop$chunkRadius : LoopRadiusRegistry.get(info.dimension());
    }

    private static boolean isFoldable(String type) {
        return "chunk".equals(type) || "entities".equals(type) || "poi".equals(type);
    }

    private static void rebase(String type, CompoundTag tag, int deltaChunkX, int deltaChunkZ) {
        if ("entities".equals(type)) {
            LoopChunkNbt.rebaseEntities(tag, deltaChunkX, deltaChunkZ);
        } else if ("poi".equals(type)) {
            LoopChunkNbt.rebasePoi(tag, deltaChunkX, deltaChunkZ);
        } else {
            LoopChunkNbt.rebase(tag, deltaChunkX, deltaChunkZ);
        }
    }

    private static ChunkPos wrap(ChunkPos pos, int radius) {
        return new ChunkPos(LoopGeometry.wrapChunk(pos.x(), radius), LoopGeometry.wrapChunk(pos.z(), radius));
    }
}
