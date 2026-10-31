package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoise;
import cc.sighs.spatialloop.world.LoopNoisePeriod;
import cc.sighs.spatialloop.world.LoopNoisePeriodAware;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 所有地形噪声的兜底入口：{@code NormalNoise#getValue}。
 *
 * <p>1.18.2 没有 {@code RandomState}，噪声实例是本模组生成器构造器里由
 * {@code NoiseGeneratorSettings#createNoiseRouter} 创建的；{@code SpatialLoopChunkGenerator#create}
 * 在那之前压入方块周期，本 mixin 在构造结束时把它盖到实例上（0 表示不周期化）。
 * 调用方可以用 {@link LoopNoisePeriod} 指定本次输入坐标的周期（例如 {@code xzScale} 或 {@code 0.25}），
 * 没有指定时按「输入就是方块坐标」用实例周期。
 */
@Mixin(NormalNoise.class)
public abstract class NormalNoiseMixin implements LoopNoisePeriodAware {
    @Shadow
    @Final
    private PerlinNoise first;

    @Shadow
    @Final
    private PerlinNoise second;

    @Shadow
    @Final
    private double valueFactor;

    @Unique
    private int spatialloop$periodBlocks;

    /** 构造器返回时本维度周期仍在线程栈上（见 {@code SpatialLoopChunkGenerator#create}）。 */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void spatialloop$stampPeriod(CallbackInfo ci) {
        int period = (int) LoopNoisePeriod.currentPeriod(0.0);
        if (period > 0) {
            this.spatialloop$periodBlocks = period;
        }
    }

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private void spatialloop$periodicValue(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        int period = this.spatialloop$periodBlocks;
        if (period <= 0) {
            return;
        }
        double inputPeriod = LoopNoisePeriod.currentPeriod(period);
        PerlinNoiseAccessor firstAccess = (PerlinNoiseAccessor) (Object) this.first;
        PerlinNoiseAccessor secondAccess = (PerlinNoiseAccessor) (Object) this.second;
        cir.setReturnValue(LoopNoise.sampleNormal(
                firstAccess.spatialloop$noiseLevels(),
                secondAccess.spatialloop$noiseLevels(),
                firstAccess.spatialloop$amplitudes(),
                firstAccess.spatialloop$firstOctave(),
                this.valueFactor,
                x,
                y * LoopNoisePeriod.currentYWeight(),
                z,
                inputPeriod
        ));
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
