package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoisePeriod;
import cc.sighs.spatialloop.world.LoopPositionalRandomFactory;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomSource;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把 {@code NoiseRouter} 里两个按坐标取种子的随机工厂折回（1.18.2 没有 {@code RandomState}，
 * 噪声路由在本模组生成器构造器里一次性建好，这时 {@link LoopNoisePeriod} 正好压着本维度的周期）。
 *
 * <ul>
 *   <li>含水层：{@code at} 的输入是 {@code blockX / 16} 的格子坐标，所以周期是 {@code 方块周期 / 16}。</li>
 *   <li>矿脉：{@code at} 的输入是方块坐标，周期就是方块周期。</li>
 * </ul>
 *
 * <p>方法里有三次 {@code RandomSource#forkPositional()}：第 0 次是种子派生出来的基础工厂
 * （{@code Algorithm#newInstance} 的返回类型就是 {@code RandomSource}），第 1、2 次才是
 * {@code "aquifer"} 与 {@code "ore"}，所以这里用 ordinal 1 和 2。
 */
@Mixin(NoiseRouterData.class)
public abstract class NoiseRouterDataMixin {
    @Redirect(
            method = "createNoiseRouter",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomSource;forkPositional()Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;",
                    ordinal = 1
            )
    )
    private static PositionalRandomFactory spatialloop$wrapAquiferFactory(RandomSource source) {
        PositionalRandomFactory factory = source.forkPositional();
        int period = (int) LoopNoisePeriod.currentPeriod(0.0);
        if (period <= 0) {
            return factory;
        }
        // 格子坐标（16 方块一格）。
        return new LoopPositionalRandomFactory(factory, Math.max(1, period / 16));
    }

    @Redirect(
            method = "createNoiseRouter",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomSource;forkPositional()Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;",
                    ordinal = 2
            )
    )
    private static PositionalRandomFactory spatialloop$wrapOreFactory(RandomSource source) {
        PositionalRandomFactory factory = source.forkPositional();
        int period = (int) LoopNoisePeriod.currentPeriod(0.0);
        if (period <= 0) {
            return factory;
        }
        // 矿脉用方块坐标。
        return new LoopPositionalRandomFactory(factory, period);
    }
}
