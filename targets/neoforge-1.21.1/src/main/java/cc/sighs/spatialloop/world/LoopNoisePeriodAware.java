package cc.sighs.spatialloop.world;

import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 由 {@code NormalNoise} 的 mixin 实现：噪声实例上盖着所在维度的方块周期，0 表示不周期化。
 *
 * <p>周期是实例属性而不是全局静态量，所以只有 {@code SpatialLoopChunkGenerator} 所在维度（
 * {@code RandomState} 在构造时从 {@link LoopNoisePeriod} 读到周期）才会周期化；下界/末地保持原版。
 */
public interface LoopNoisePeriodAware {
    int spatialloop$periodBlocks();

    void spatialloop$periodBlocks(int periodBlocks);

    static int periodOf(NormalNoise noise) {
        return noise instanceof LoopNoisePeriodAware aware ? aware.spatialloop$periodBlocks() : 0;
    }
}
