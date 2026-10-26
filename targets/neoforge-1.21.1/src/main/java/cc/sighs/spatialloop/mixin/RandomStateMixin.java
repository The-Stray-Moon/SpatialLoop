package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoisePeriod;
import cc.sighs.spatialloop.world.LoopNoisePeriodAware;
import cc.sighs.spatialloop.world.LoopPositionalRandomFactory;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给一个 {@code RandomState} 里创建的所有噪声盖上周期，并把按坐标取种子的随机工厂折回。
 *
 * <p>周期来自 {@link LoopNoisePeriod}：{@code ChunkMapMixin} 在 {@code RandomState.create} 外层压入
 * 当前维度的周期，只有循环维度才非零，所以下界/末地的噪声保持原版。
 *
 * <p>注入点必须在 {@code noiseIntances} 赋值之后：那时 {@code super} 已返回、{@code this} 可用，且
 * {@code surfaceSystem}（用 {@code this.random} 构造）与噪声路由还没创建。不能用构造器 HEAD。
 */
@Mixin(RandomState.class)
public abstract class RandomStateMixin {
    @Shadow
    @Final
    @Mutable
    private PositionalRandomFactory random;

    @Shadow
    @Final
    @Mutable
    private PositionalRandomFactory aquiferRandom;

    @Shadow
    @Final
    @Mutable
    private PositionalRandomFactory oreRandom;

    @Unique
    private int spatialloop$periodBlocks;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;noiseIntances:Ljava/util/Map;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void spatialloop$installPeriod(CallbackInfo ci) {
        int period = (int) LoopNoisePeriod.currentPeriod(0.0);
        if (period <= 0) {
            return;
        }
        this.spatialloop$periodBlocks = period;
        // 地表深度抖动、矿脉、世界生成随机、竖直渐变：输入都是方块坐标。
        this.random = new LoopPositionalRandomFactory(this.random, period);
        // 含水层的流体中心是 16×12×16 的格子坐标。
        this.aquiferRandom = new LoopPositionalRandomFactory(this.aquiferRandom, Math.max(1, period / 16));
        this.oreRandom = new LoopPositionalRandomFactory(this.oreRandom, period);
    }

    @Inject(method = "getOrCreateNoise", at = @At("RETURN"))
    private void spatialloop$stampPeriod(
            ResourceKey<NormalNoise.NoiseParameters> key,
            CallbackInfoReturnable<NormalNoise> cir
    ) {
        NormalNoise noise = cir.getReturnValue();
        int period = this.spatialloop$periodBlocks;
        if (noise != null && period > 0) {
            ((LoopNoisePeriodAware) noise).spatialloop$periodBlocks(period);
        }
    }

    @Inject(method = "getOrCreateRandomFactory", at = @At("RETURN"), cancellable = true)
    private void spatialloop$wrapRandomFactory(
            ResourceLocation name,
            CallbackInfoReturnable<PositionalRandomFactory> cir
    ) {
        int period = this.spatialloop$periodBlocks;
        PositionalRandomFactory factory = cir.getReturnValue();
        if (period > 0 && factory != null && !(factory instanceof LoopPositionalRandomFactory)) {
            cir.setReturnValue(new LoopPositionalRandomFactory(factory, period));
        }
    }
}
