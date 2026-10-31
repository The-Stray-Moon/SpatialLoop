package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopSimulation;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code ServerChunkCache} 构造时把循环半径交给 {@code DistanceManager}。
 *
 * <p>注入点选在 {@code distanceManager} 字段赋值之后、构造器里第一次
 * {@code updateSimulationDistance(...)} 之前，这样初始模拟距离也会被压到周期以下。
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    /** 1.20.1 里这个字段是包级私有（非 public），shadow 声明与目标可见性保持一致。 */
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    @Final
    public ChunkMap chunkMap;

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void spatialloop$initSimulationRadius(CallbackInfo ci) {
        int radius = ((ChunkMapAccessor) this.chunkMap).spatialloop$generator()
                instanceof SpatialLoopChunkGenerator generator
                ? generator.radius()
                : LoopLevel.radiusOf(this.level);
        ((LoopSimulation) this.distanceManager).spatialloop$setSimulationRadius(radius);
        if (radius > 0) {
            // 构造器里的首次 updateSimulationDistance 发生在半径写入之前；按原版配置值重放一次，
            // DistanceManagerMixin 会把它压到 maxDistance(radius)。
            ServerChunkCache self = (ServerChunkCache) (Object) this;
            self.setSimulationDistance(this.level.getServer().getPlayerList().getSimulationDistance());
        }
    }
}
