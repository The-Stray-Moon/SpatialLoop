package cc.sighs.spatialloop.world;

import net.minecraft.world.level.Level;

/**
 * 由 {@code ServerLevel} 的 mixin 实现的旁路接口：把这个维度「空间循环区块半径」暴露给视距限制。
 *
 * <p>半径来自维度生成器本身（也就是 level.dat 里保存的生成器设置），所以重开存档、换客户端都不会丢。
 * 0 表示该维度没有启用循环。
 */
public interface LoopLevel {
    int spatialloop$loopRadius();

    static int radiusOf(Level level) {
        return level instanceof LoopLevel loopLevel ? loopLevel.spatialloop$loopRadius() : 0;
    }
}
