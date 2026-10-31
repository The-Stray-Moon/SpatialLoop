package cc.sighs.spatialloop.mixin;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.GenerationChunkHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code generationRefCount} 在 26.1 里是私有原子计数，没有公开的读取方法。 */
@Mixin(GenerationChunkHolder.class)
public interface GenerationChunkHolderAccessor {
    @Accessor("generationRefCount")
    AtomicInteger spatialloop$generationRefCount();
}
