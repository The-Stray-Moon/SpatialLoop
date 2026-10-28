package cc.sighs.spatialloop.world;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;

/**
 * ChunkStorage 与 entities/poi SimpleRegionStorage 的真实写入屏障。
 * 仅供停止旧副本生产者后的交接使用；获取快照本身不卸载区块，也不阻止新的写入。
 */
public interface LoopStorageBarrier {
    CompletableFuture<Void> spatialloop$writeBarrier(ChunkPos pos);
}
