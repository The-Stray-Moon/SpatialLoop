package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopSimulation;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 模拟距离压到周期以下，保证同余的两块区块不会同时处于完整/模拟状态。
 *
 * <p>半径由 {@code ServerChunkCache} 构造时写入（见 {@link ServerChunkCacheMixin}）。
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin implements LoopSimulation {
    @Unique
    private int spatialloop$simulationRadius;

    @ModifyVariable(method = "updateSimulationDistance", at = @At("HEAD"), argsOnly = true, index = 1)
    private int spatialloop$clampSimulationDistance(int value) {
        int radius = this.spatialloop$simulationRadius;
        if (radius <= 0) {
            return value;
        }
        // 周期是 2r 区块；只要加载半径 < 2r，就不会有两块同余区块同时存在。
        return Math.min(value, 2 * radius - 1);
    }

    @Override
    public int spatialloop$simulationRadius() {
        return this.spatialloop$simulationRadius;
    }

    @Override
    public void spatialloop$setSimulationRadius(int radius) {
        this.spatialloop$simulationRadius = radius;
    }
}
