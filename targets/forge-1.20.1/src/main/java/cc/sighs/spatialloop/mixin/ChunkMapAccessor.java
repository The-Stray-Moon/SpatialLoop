package cc.sighs.spatialloop.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1 的 {@code ChunkMap.generator} 是 private；构造期间需要直接读它来判断循环半径。 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("generator")
    ChunkGenerator spatialloop$generator();
}
