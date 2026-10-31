package cc.sighs.spatialloop.mixin;

import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.18.2 的 {@code NoiseBasedChunkGenerator#settings} 是 protected，没有公开 getter；创建世界界面要读它。 */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {
    @Accessor("settings")
    Holder<NoiseGeneratorSettings> spatialloop$settings();
}
