package cc.sighs.spatialloop.world;

/**
 * 由 {@code ChunkStorage} 的 mixin 实现：把区块存档坐标折回循环区域。
 *
 * <p>内存里的区块仍然按玩家坐标一一存在（这样方块实体、实体、发包都不需要额外的坐标翻译），
 * 但读写磁盘时统一落到折回后的槽位，因此区域文件始终只覆盖那 {@code 2r × 2r} 个区块。
 */
public interface LoopStorage {
    int spatialloop$chunkRadius();

    void spatialloop$setChunkRadius(int radius);
}
