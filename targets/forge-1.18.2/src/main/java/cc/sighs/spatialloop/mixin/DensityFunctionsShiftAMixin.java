package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopShiftNoise;
import javax.annotation.Nullable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code shift_a}：输入 {@code (blockX, 0, blockZ) * 0.25}。 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftA")
public abstract class DensityFunctionsShiftAMixin {
    @Shadow
    @Final
    @Nullable
    private NormalNoise offsetNoise;

    @Inject(
            method = "compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D",
            at = @At("HEAD"),
            cancellable = true
    )
    private void spatialloop$periodicCompute(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(LoopShiftNoise.sample(this.offsetNoise, context.blockX(), 0.0, context.blockZ()));
    }
}
