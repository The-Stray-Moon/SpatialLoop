package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopRadiusRegistry;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import java.util.List;
import java.util.concurrent.Executor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code ServerLevel} 构造过程中把生成器里的循环半径记下来。
 *
 * <p>注入点是构造器里第一次给 {@code tickTime} 赋值之后：此时 {@code super(...)} 已经返回、{@code this}
 * 可用，而 {@code ServerChunkCache}/{@code ChunkMap} 还没创建——{@code ChunkMap} 构造时会立刻调用
 * {@code setServerViewDistance}，那时 {@code level.getChunkSource()} 尚不存在，只能靠这个字段。
 *
 * <p>不能用 {@code @At("HEAD")}：构造器的 HEAD 在 {@code super()} 之前，注入处理器必须是 static，
 * 无法写实例字段。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements LoopLevel {
    @Unique
    private int spatialloop$loopRadius;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/level/ServerLevel;tickTime:Z",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void spatialloop$captureLoopRadius(
            MinecraftServer server,
            Executor executor,
            LevelStorageSource.LevelStorageAccess storageAccess,
            ServerLevelData levelData,
            ResourceKey<Level> dimension,
            LevelStem stem,
            ChunkProgressListener progressListener,
            boolean debug,
            long seed,
            List<CustomSpawner> customSpawners,
            boolean tickTime,
            RandomSequences randomSequences,
            CallbackInfo ci
    ) {
        if (stem.generator() instanceof SpatialLoopChunkGenerator generator) {
            this.spatialloop$loopRadius = generator.radius();
        }
        LoopRadiusRegistry.put(dimension, this.spatialloop$loopRadius);
    }

    @Override
    public int spatialloop$loopRadius() {
        return this.spatialloop$loopRadius;
    }
}
