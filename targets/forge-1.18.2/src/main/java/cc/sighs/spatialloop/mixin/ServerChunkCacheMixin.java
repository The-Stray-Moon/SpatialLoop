package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopSimulation;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code ServerChunkCache} 构造时把循环半径交给 {@code DistanceManager}。
 *
 * <p>1.18.2 的构造器在返回前会调用一次 {@code updateSimulationDistance(...)}，那时半径还没写入；
 * 因此 RETURN 处写入半径后，按原版配置值重放一次，{@code DistanceManagerMixin} 会把它压到
 * {@code maxDistance(radius)}。
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {
    @Shadow
    @Final
    public ServerLevel level;

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
            ServerChunkCache self = (ServerChunkCache) (Object) this;
            self.setSimulationDistance(this.level.getServer().getPlayerList().getSimulationDistance());
        }
    }
}
