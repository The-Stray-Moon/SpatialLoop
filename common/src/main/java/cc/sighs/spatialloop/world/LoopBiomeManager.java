package cc.sighs.spatialloop.world;

/**
 * 由 {@code BiomeManager} 的 mixin 实现：把生物群系的「模糊查找」也限制在一个周期内。
 *
 * <p>区块里存的生物群系本身是周期的（气候噪声已经周期化），但 {@code BiomeManager#getBiome}
 * 会在目标位置附近用绝对坐标哈希挑一个抖动后的 4×4×4 格，副本之间抖动不同，生物群系边界上就会
 * 一格之差。feature 的 {@code BiomeFilter}、地表规则、刷怪都走这里。
 */
public interface LoopBiomeManager {
    int spatialloop$periodBlocks();

    void spatialloop$periodBlocks(int periodBlocks);
}
