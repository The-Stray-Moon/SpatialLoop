package cc.sighs.spatialloop.world;

import net.minecraft.world.level.levelgen.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * 把按坐标取种子的随机工厂折回一个周期（1.18.2 版）。
 *
 * <p>1.18.2 的 {@link PositionalRandomFactory} 只有 {@code at}/{@code fromHashOf}/
 * {@code parityConfigString}，没有 1.20.1 才加入的 {@code fromSeed(long)}，所以这里少了那个重写。
 * 语义与 common 版一致：只折 {@code at(x, y, z)} 的 x/z 输入，种子派生原样透传。
 */
public final class LoopPositionalRandomFactory implements PositionalRandomFactory {
    private final PositionalRandomFactory delegate;
    private final int xzPeriod;

    public LoopPositionalRandomFactory(PositionalRandomFactory delegate, int xzPeriod) {
        this.delegate = delegate;
        this.xzPeriod = Math.max(1, xzPeriod);
    }

    @Override
    public RandomSource at(int x, int y, int z) {
        return this.delegate.at(Math.floorMod(x, this.xzPeriod), y, Math.floorMod(z, this.xzPeriod));
    }

    /** 包装时使用的 x/z 周期（以 {@code at} 的坐标单位计）。 */
    public int xzPeriod() {
        return this.xzPeriod;
    }

    @Override
    public RandomSource fromHashOf(String name) {
        return this.delegate.fromHashOf(name);
    }

    @Override
    public void parityConfigString(StringBuilder builder) {
        this.delegate.parityConfigString(builder);
    }
}
