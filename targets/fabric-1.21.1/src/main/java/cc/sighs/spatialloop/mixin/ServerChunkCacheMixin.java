package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopSimulation;
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
    @Shadow
    @Final
    public ServerLevel level;

    @Shadow
    @Final
    private DistanceManager distanceManager;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/level/ServerChunkCache;distanceManager:Lnet/minecraft/server/level/DistanceManager;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void spatialloop$initSimulationRadius(CallbackInfo ci) {
        ((LoopSimulation) this.distanceManager).spatialloop$setSimulationRadius(LoopLevel.radiusOf(this.level));
    }
}
