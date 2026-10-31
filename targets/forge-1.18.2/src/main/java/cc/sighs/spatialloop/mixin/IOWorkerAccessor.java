package cc.sighs.spatialloop.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.18.2 的 {@code IOWorker#loadAsync} 是 protected，实体存档折回需要从 mixin 里调用它。 */
@Mixin(IOWorker.class)
public interface IOWorkerAccessor {
    @Invoker("loadAsync")
    CompletableFuture<CompoundTag> spatialloop$loadAsync(ChunkPos pos);
}
