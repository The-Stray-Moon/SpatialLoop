package cc.sighs.spatialloop.world;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** 在创建生成任务、取得任何依赖引用之前原子准入整个范围。仅在服务器主线程调用。 */
public interface LoopGenerationAdmission {
    boolean spatialloop$admit(GenerationChunkHolder holder, ChunkStatus status);

    void spatialloop$pumpHandoffs();
}
