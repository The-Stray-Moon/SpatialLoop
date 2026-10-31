package cc.sighs.spatialloop.mixin;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读 {@code PerlinNoise} 的八度表：周期采样的振幅与频率必须和原版完全一致。 */
@Mixin(PerlinNoise.class)
public interface PerlinNoiseAccessor {
    @Accessor("noiseLevels")
    ImprovedNoise[] spatialloop$noiseLevels();

    @Accessor("amplitudes")
    DoubleList spatialloop$amplitudes();

    @Accessor("firstOctave")
    int spatialloop$firstOctave();
}
