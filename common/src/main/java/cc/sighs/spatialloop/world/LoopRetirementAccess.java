package cc.sighs.spatialloop.world;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;

/** 主线程上的实体卸载观测；调用方必须先停止旧区块的生产者。 */
public interface LoopRetirementAccess {
    void spatialloop$pumpEntities();

    boolean spatialloop$entitiesRetired(ChunkPos pos);

    CompletableFuture<Void> spatialloop$entityWriteBarrier(ChunkPos pos);
}
