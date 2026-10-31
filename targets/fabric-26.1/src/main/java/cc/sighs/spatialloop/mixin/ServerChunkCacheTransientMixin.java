package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopTransientTickets;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheTransientMixin {
    // 26.1 的票据表挂在 TicketStorage 上，DistanceManager 只剩距离跟踪。
    @Shadow @Final
    private TicketStorage ticketStorage;

    @Shadow @Final
    public ServerLevel level;

    @Inject(method = "getChunkFutureMainThread", at = @At("HEAD"))
    private void spatialloop$beginTransientRequest(int x, int z, ChunkStatus status, boolean create,
            CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir) {
        if (create && LoopLevel.radiusOf(this.level) > 0) {
            ((LoopTransientTickets) this.ticketStorage).beginRequest(ChunkPos.pack(x, z));
        }
    }

    @Inject(method = "getChunkFutureMainThread", at = @At("RETURN"))
    private void spatialloop$finishTransientRequest(int x, int z, ChunkStatus status, boolean create,
            CallbackInfoReturnable<CompletableFuture<ChunkResult<ChunkAccess>>> cir) {
        if (create && LoopLevel.radiusOf(this.level) > 0) {
            long source = ChunkPos.pack(x, z);
            LoopTransientTickets requests = (LoopTransientTickets) this.ticketStorage;
            cir.getReturnValue().whenComplete((result, failure) -> requests.finishRequest(source));
        }
    }
}
