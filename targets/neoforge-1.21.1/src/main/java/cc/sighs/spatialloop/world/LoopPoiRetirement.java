package cc.sighs.spatialloop.world;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.level.ChunkPos;

public interface LoopPoiRetirement {
    /** 仅在主线程、正常 holder 完全卸载后调用；不等待 IO。 */
    CompletableFuture<Void> spatialloop$retirePoi(ChunkPos pos);
}
