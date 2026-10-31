package cc.sighs.spatialloop.world;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * 把按坐标取种子的随机工厂折回一个周期。
 *
 * <p>原版地形里还有几处「按绝对坐标哈希」的随机：含水层的流体中心（以 16×12×16 的格子为单位）、
 * 矿脉（方块坐标）、地表深度抖动与竖直渐变（方块坐标）。只周期化噪声不够——这些随机数在副本之间
 * 不同，洞穴水和地表厚度就会分叉。包装后的工厂只折 {@code at(x, y, z)} 的 x/z 输入，种子派生
 * （{@code fromHashOf}/{@code fromSeed}）原样透传。
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
    public RandomSource fromSeed(long seed) {
        return this.delegate.fromSeed(seed);
    }

    @Override
    public void parityConfigString(StringBuilder builder) {
        this.delegate.parityConfigString(builder);
    }
}
