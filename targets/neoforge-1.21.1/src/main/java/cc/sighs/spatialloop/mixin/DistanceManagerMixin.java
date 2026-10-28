package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopLoadingLimits;
import cc.sighs.spatialloop.world.LoopSimulation;
import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 模拟距离压到周期以下以限制模拟范围；这不能保证同余区块互斥。
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
        // 这是距离上限，不是互斥保证：加载范围的直径及延迟卸载仍可能覆盖同余副本。
        return Math.min(value, LoopLoadingLimits.maxDistance(radius));
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
