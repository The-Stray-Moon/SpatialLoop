package cc.sighs.spatialloop.world;

import net.minecraft.core.BlockPos;

/**
 * 装饰/地表阶段的方块周期（线程内）。
 *
 * <p>原版还有几处按方块坐标直接哈希的 {@code PerlinSimplexNoise} 静态实例：
 * {@code Biome.BIOME_INFO_NOISE}（海草/海带/花草的 noise_based_count、noise_threshold_count）、
 * {@code TEMPERATURE_NOISE}/{@code FROZEN_TEMPERATURE_NOISE}（冻洋冰山）。它们不归 {@code RandomState}
 * 管，没法在实例上盖周期；所以在同步执行的装饰/地表阶段压一个周期，调用点把方块坐标折回。
 */
public final class LoopFeatureNoise {
    private static final ThreadLocal<Integer> PERIOD = new ThreadLocal<>();

    private LoopFeatureNoise() {
    }

    public static void push(int periodBlocks) {
        if (periodBlocks > 0) {
            PERIOD.set(periodBlocks);
        } else {
            PERIOD.remove();
        }
    }

    public static void pop() {
        PERIOD.remove();
    }

    public static int wrap(int block) {
        Integer period = PERIOD.get();
        if (period == null || period <= 0) {
            return block;
        }
        return Math.floorMod(block + period / 2, period) - period / 2;
    }

    /** 折回一个方块坐标；没有周期或坐标没变时返回原对象，避免每次调用都分配。 */
    public static BlockPos wrap(BlockPos pos) {
        int x = wrap(pos.getX());
        int z = wrap(pos.getZ());
        return x == pos.getX() && z == pos.getZ() ? pos : new BlockPos(x, pos.getY(), z);
    }
}
