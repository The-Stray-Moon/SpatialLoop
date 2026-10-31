package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopPoiRetirement;
import cc.sighs.spatialloop.world.LoopStorageBarrier;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PoiManager.class)
public abstract class PoiManagerRetirementMixin implements LoopPoiRetirement {
    @Shadow @Final private LongSet loadedChunks;
    @Shadow protected abstract void onSectionLoad(long section);

    @Override
    public CompletableFuture<Void> spatialloop$retirePoi(ChunkPos pos) {
        PoiManager manager = (PoiManager) (Object) this;
        SectionStorageRetirementAccessor storage = (SectionStorageRetirementAccessor) this;
        // NeoForge CommonHooks.onChunkUnload already flushes and removes the column.
        // A second flush is a no-op unless a late producer dirtied it after that hook.
        try {
            manager.flush(pos);
            LevelHeightAccessor height = storage.spatialloop$heightAccessor();
            for (int y = height.getMinSection(); y < height.getMaxSection(); y++) {
                long section = SectionPos.asLong(pos.x, y, pos.z);
                storage.spatialloop$sectionCache().remove(section);
                storage.spatialloop$dirtySections().remove(section);
                // Recompute the distance source after eviction, without loading the column.
                // Vanilla processes this queued update before the next distance query.
                this.onSectionLoad(section);
            }
            this.loadedChunks.remove(pos.toLong());
            return ((LoopStorageBarrier) storage.spatialloop$regionStorage()).spatialloop$writeBarrier(pos);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
