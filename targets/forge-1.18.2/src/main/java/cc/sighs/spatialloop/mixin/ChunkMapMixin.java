package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopLoadingLimits;
import cc.sighs.spatialloop.world.LoopStorage;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端的两件事：
 *
 * <ul>
 *   <li>构造时把循环半径写进 {@code ChunkStorage}（1.18.2 的 {@code ChunkMap} 直接继承
 *       {@code ChunkStorage}），让区块存档坐标折回（见 {@link ChunkStorageMixin}）。</li>
 *   <li>把 {@code viewDistance}（{@code ChunkMap#setViewDistance}）压到预留生成依赖圈后的安全距离：
 *       每个玩家的区块跟踪取 {@code min(客户端请求, viewDistance)}，压住它，加载、发送、渲染就都跟着收窄。</li>
 * </ul>
 *
 * <p>1.18.2 没有 {@code RandomState}，噪声周期在生成器构造器里安装（见
 * {@code SpatialLoopChunkGenerator#create}），这里不需要再接管。
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    private ChunkGenerator generator;

    @Unique
    private int spatialloop$radius() {
        // 构造期间 ServerLevel 的 LoopLevel 字段尚未落地，直接读本 ChunkMap 的生成器。
        if (this.generator instanceof SpatialLoopChunkGenerator spatialLoopGenerator) {
            return spatialLoopGenerator.radius();
        }
        return LoopLevel.radiusOf(this.level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void spatialloop$initStorageRadius(CallbackInfo ci) {
        ((LoopStorage) (Object) this).spatialloop$setChunkRadius(this.spatialloop$radius());
    }

    /**
     * 1.18.2 的 {@code ChunkMap#setViewDistance} 同时被构造器和 {@code ServerChunkCache#setViewDistance}
     * 调用，压住它即可收窄加载、发送与渲染范围。
     */
    @ModifyVariable(method = "setViewDistance", at = @At("HEAD"), argsOnly = true)
    private int spatialloop$clampServerViewDistance(int viewDistance) {
        int radius = this.spatialloop$radius();
        return radius > 0 ? Math.min(viewDistance, LoopLoadingLimits.maxDistance(radius)) : viewDistance;
    }
}
