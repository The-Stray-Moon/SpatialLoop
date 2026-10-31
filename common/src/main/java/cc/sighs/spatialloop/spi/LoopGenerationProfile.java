package cc.sighs.spatialloop.spi;

/**
 * 目标版本原版区块票据的生成依赖参数。
 *
 * <p>这是「版本差异」的 SPI：公式在 common，具体数字由各 target 按自己版本的
 * {@code ChunkPyramid}/{@code ChunkLevel} 提供。1.21.1 是
 * {@code FULL reach = 11}、{@code MAX_LEVEL - ENTITY_TICKING = 13}；1.20.1 及更早
 * 没有 {@code ChunkPyramid}，依赖半径不同，必须由对应 target 实现。
 */
public interface LoopGenerationProfile {
    /** 请求 {@code FULL} 状态时的生成依赖半径（区块）。 */
    int generationRadius();

    /** 票据方形之外仍被原版保留/生成的区块圈数（{@code MAX_LEVEL - 玩家票据等级}）。 */
    int loadedHaloRadius();
}
