package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopShiftNoise;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code shift_b}：原版输入是 {@code (blockZ, blockX, 0) * 0.25}——第三个坐标被放进了噪声的 y 轴，
 * 而 y 轴不折回，副本之间就会分叉。
 *
 * <p>这里改成 {@code (blockZ, 0, blockX) * 0.25}：两个随 x/z 变化的坐标都落在会折回的轴上。shift 只是
 * 地形扭曲，换一个同样平滑、同样周期的扭曲场没有副作用。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$ShiftB")
public abstract class DensityFunctionsShiftBMixin {
    @Shadow
    @Final
    private DensityFunction.NoiseHolder offsetNoise;

    @Inject(
            method = "compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D",
            at = @At("HEAD"),
            cancellable = true
    )
    private void spatialloop$periodicCompute(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(LoopShiftNoise.sample(this.offsetNoise, context.blockZ(), 0.0, context.blockX()));
    }
}
