package cc.sighs.spatialloop.world;

import net.minecraft.world.level.chunk.status.ChunkStatus;

/** 主线程延后生成时保存的最高目标状态与诊断计时。 */
public record LoopPendingGeneration(ChunkStatus status, long since) { }
