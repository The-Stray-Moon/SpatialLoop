package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopShiftNoise;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code shift}：输入 {@code (blockX, blockY, blockZ) * 0.25}。 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Shift")
public abstract class DensityFunctionsShiftMixin {
    @Shadow
    @Final
    private DensityFunction.NoiseHolder offsetNoise;

    @Inject(
            method = "compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D",
            at = @At("HEAD"),
            cancellable = true
    )
    private void spatialloop$periodicCompute(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(LoopShiftNoise.sample(
                this.offsetNoise, context.blockX(), context.blockY(), context.blockZ()
        ));
    }
}
