package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoisePeriod;
import cc.sighs.spatialloop.world.LoopNoisePeriodAware;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code base_3d_noise}：{@code BlendedNoise} 是密度函数里唯一不走 {@code NormalNoise} 的噪声，
 * 它直接调 {@code ImprovedNoise}。它按方块坐标乘以 684.412 再取八度，所以只要把进来的 x/z 折回
 * 一个周期，整个场就是周期的，接缝也连续（原函数本身连续）。
 *
 * <p>1.18.2 没有 {@code RandomState#withNewRandom}：实例直接在本模组生成器构造器里创建
 * （{@code NoiseRouterData#createNoiseRouter}），所以周期在构造结束时从 {@link LoopNoisePeriod}
 * 读取。下界/末地没有周期，保持原版。
 */
@Mixin(BlendedNoise.class)
public abstract class BlendedNoiseMixin implements LoopNoisePeriodAware {
    @Unique
    private int spatialloop$periodBlocks;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void spatialloop$stampPeriod(CallbackInfo ci) {
        int period = (int) LoopNoisePeriod.currentPeriod(0.0);
        if (period > 0) {
            this.spatialloop$periodBlocks = period;
        }
    }

    @Redirect(
            method = "compute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;blockX()I"
            )
    )
    private int spatialloop$periodicBlockX(DensityFunction.FunctionContext context) {
        return spatialloop$wrap(context.blockX());
    }

    @Redirect(
            method = "compute",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;blockZ()I"
            )
    )
    private int spatialloop$periodicBlockZ(DensityFunction.FunctionContext context) {
        return spatialloop$wrap(context.blockZ());
    }

    @Unique
    private int spatialloop$wrap(int block) {
        int period = this.spatialloop$periodBlocks;
        if (period <= 0) {
            return block;
        }
        return Math.floorMod(block + period / 2, period) - period / 2;
    }

    @Override
    public int spatialloop$periodBlocks() {
        return this.spatialloop$periodBlocks;
    }

    @Override
    public void spatialloop$periodBlocks(int periodBlocks) {
        this.spatialloop$periodBlocks = periodBlocks;
    }
}
