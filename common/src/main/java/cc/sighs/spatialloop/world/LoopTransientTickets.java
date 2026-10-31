package cc.sighs.spatialloop.world;

import net.minecraft.world.level.ChunkPos;

public interface LoopTransientTickets {
    void beginRequest(long source);

    void finishRequest(long source);

    /** Called only on the server main thread, when an old dependency blocks admission. */
    boolean retireCompletedRequests(ChunkPos dependency);
}
