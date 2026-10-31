package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopRetirementAccess;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRetirementMixin implements LoopRetirementAccess {
    @Shadow @Final private PersistentEntitySectionManager<Entity> entityManager;

    @Override
    public void spatialloop$pumpEntities() {
        this.entityManager.tick();
    }

    @Override
    public boolean spatialloop$entitiesRetired(ChunkPos pos) {
        return ((LoopRetirementAccess) this.entityManager).spatialloop$entitiesRetired(pos);
    }

    @Override
    public CompletableFuture<Void> spatialloop$entityWriteBarrier(ChunkPos pos) {
        return ((LoopRetirementAccess) this.entityManager).spatialloop$entityWriteBarrier(pos);
    }
}
