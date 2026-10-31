package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoisePeriod;
import cc.sighs.spatialloop.world.LoopNoisePeriodAware;
import cc.sighs.spatialloop.world.LoopPositionalRandomFactory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code SurfaceSystem} 里少数带缩放的地表噪声调用。
 *
 * <p>直接传方块坐标的调用（{@code getSurfaceDepth}/{@code getSurfaceSecondary}/{@code clayBands}）
 * 走 {@code NormalNoise} 的默认方块周期，不用在这里处理；恶地与冻洋的柱状噪声分别乘了 0.2/0.75/1.28/1.17，
 * 必须把输入周期一起缩放，否则副本的恶地/冰山地形会分叉。
 */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemMixin {
    @Shadow
    @Final
    @Mutable
    private PositionalRandomFactory noiseRandom;

    /**
     * 1.20.1 的 Mixin 不能在 RandomState 构造器中途注入，因此在 SurfaceSystem 构造结束时包装
     * 它持有的 positional factory；{@code clayBands} 用 {@code fromHashOf} 生成，不依赖坐标，
     * 包装前后结果一致。
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void spatialloop$wrapNoiseRandom(
            RandomState state,
            BlockState defaultBlock,
            int seaLevel,
            PositionalRandomFactory factory,
            CallbackInfo ci
    ) {
        int period = (int) LoopNoisePeriod.currentPeriod(0.0);
        if (period > 0) {
            this.noiseRandom = new LoopPositionalRandomFactory(factory, period);
        }
    }
    @Redirect(
            method = "erodedBadlandsExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 1
            )
    )
    private double spatialloop$badlandsPillar(NormalNoise noise, double x, double y, double z) {
        return spatialloop$sample(noise, x, y, z, 0.2);
    }

    @Redirect(
            method = "erodedBadlandsExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 2
            )
    )
    private double spatialloop$badlandsPillarRoof(NormalNoise noise, double x, double y, double z) {
        return spatialloop$sample(noise, x, y, z, 0.75);
    }

    @Redirect(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 1
            )
    )
    private double spatialloop$icebergPillar(NormalNoise noise, double x, double y, double z) {
        return spatialloop$sample(noise, x, y, z, 1.28);
    }

    @Redirect(
            method = "frozenOceanExtension",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D",
                    ordinal = 2
            )
    )
    private double spatialloop$icebergPillarRoof(NormalNoise noise, double x, double y, double z) {
        return spatialloop$sample(noise, x, y, z, 1.17);
    }

    @Unique
    private static double spatialloop$sample(NormalNoise noise, double x, double y, double z, double scale) {
        int period = LoopNoisePeriodAware.periodOf(noise);
        if (period <= 0) {
            return noise.getValue(x, y, z);
        }
        LoopNoisePeriod.push(period * scale, 1.0);
        try {
            return noise.getValue(x, y, z);
        } finally {
            LoopNoisePeriod.pop();
        }
    }
}
