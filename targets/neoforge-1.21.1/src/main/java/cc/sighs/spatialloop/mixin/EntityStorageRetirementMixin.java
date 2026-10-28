package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopRadiusRegistry;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityStorage.class)
public abstract class EntityStorageRetirementMixin implements LoopStorageBarrier {
    @Shadow @Final private SimpleRegionStorage simpleRegionStorage;
    @Shadow @Final private LongSet emptyChunks;
    @Unique private static final ThreadLocal<java.util.function.Consumer<Throwable>> spatialloop$saveFailure = new ThreadLocal<>();
    @Unique private final Long2ObjectMap<Throwable> spatialloop$serializationFailures = new Long2ObjectOpenHashMap<>();

    @Unique
    private long spatialloop$canonicalKey(ChunkPos pos) {
        int radius = LoopRadiusRegistry.get(this.simpleRegionStorage.storageInfo().dimension());
        return radius > 0 ? ChunkPos.asLong(LoopGeometry.wrapChunk(pos.x, radius), LoopGeometry.wrapChunk(pos.z, radius)) : pos.toLong();
    }

    @Inject(method = "storeEntities", at = @At("HEAD"))
    private void spatialloop$beginStore(ChunkEntities<Entity> entities, CallbackInfo ci) {
        if (LoopRadiusRegistry.get(this.simpleRegionStorage.storageInfo().dimension()) > 0) {
            long key = this.spatialloop$canonicalKey(entities.getPos());
            spatialloop$saveFailure.set(failure -> this.spatialloop$serializationFailures.putIfAbsent(key, failure));
        } else {
            spatialloop$saveFailure.remove();
        }
    }

    @Inject(method = "storeEntities", at = @At("RETURN"))
    private void spatialloop$endStore(ChunkEntities<Entity> entities, CallbackInfo ci) {
        spatialloop$saveFailure.remove();
    }

    // NeoForge catches serialization exceptions inside a static synthetic forEach lambda.
    // Record the failure before its catch logs and discards the entity; never hand off this slot.
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;save(Lnet/minecraft/nbt/CompoundTag;)Z"), require = 1)
    private static boolean spatialloop$recordSerializationFailure(Entity entity, CompoundTag tag) {
        try {
            return entity.save(tag);
        } catch (RuntimeException failure) {
            java.util.function.Consumer<Throwable> report = spatialloop$saveFailure.get();
            if (report != null) {
                report.accept(failure);
            }
            throw failure;
        }
    }

    @Override
    public CompletableFuture<Void> spatialloop$writeBarrier(ChunkPos pos) {
        long key = this.spatialloop$canonicalKey(pos);
        // 同余副本的空缓存也必须失效，否则新主人可能绕过真实存储读取。
        this.emptyChunks.removeIf((long cached) -> this.spatialloop$canonicalKey(new ChunkPos(cached)) == key);
        Throwable failure = this.spatialloop$serializationFailures.get(key);
        if (failure != null) {
            return CompletableFuture.failedFuture(failure);
        }
        return ((LoopStorageBarrier) this.simpleRegionStorage).spatialloop$writeBarrier(pos);
    }
}
