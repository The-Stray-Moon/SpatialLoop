package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.ownership.LoopTransientRequests;
import cc.sighs.spatialloop.world.LoopTransientTickets;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.TicketStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 26.1 把票据表从 {@code DistanceManager} 移到了 {@code TicketStorage}，{@code DistanceManager}
 * 只保留距离跟踪。临时请求的登记与退休因此挂在 {@code TicketStorage} 上。
 */
@Mixin(TicketStorage.class)
public abstract class DistanceManagerTransientMixin implements LoopTransientTickets {
    @Shadow @Final
    private Long2ObjectOpenHashMap<List<Ticket>> tickets;

    @Shadow
    public abstract boolean removeTicket(long key, Ticket ticket);

    @Unique
    private final LoopTransientRequests spatialloop$transientRequests = new LoopTransientRequests();

    @Override
    public void beginRequest(long source) {
        this.spatialloop$transientRequests.begin(source);
    }

    @Override
    public void finishRequest(long source) {
        this.spatialloop$transientRequests.finish(source);
    }

    @Override
    public boolean retireCompletedRequests(ChunkPos dependency) {
        List<Map.Entry<Long, Ticket>> removals = new ArrayList<>();
        for (var entry : this.tickets.long2ObjectEntrySet()) {
            long source = entry.getLongKey();
            if (!this.spatialloop$transientRequests.canRetire(source)) {
                continue;
            }
            ChunkPos sourcePos = ChunkPos.unpack(source);
            long distance = Math.max(Math.abs((long) sourcePos.x() - dependency.x()),
                    Math.abs((long) sourcePos.z() - dependency.z()));
            for (Ticket ticket : entry.getValue()) {
                if (ticket.getType() == TicketType.UNKNOWN
                        && distance <= (long) ChunkLevel.MAX_LEVEL - ticket.getTicketLevel()) {
                    removals.add(Map.entry(source, ticket));
                }
            }
        }
        // Do not run updates here: admission can already be inside a distance update.
        for (var removal : removals) {
            this.removeTicket(removal.getKey(), removal.getValue());
        }
        return !removals.isEmpty();
    }
}
