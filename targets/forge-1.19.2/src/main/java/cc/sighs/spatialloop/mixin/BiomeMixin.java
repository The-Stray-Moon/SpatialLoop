package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopFeatureNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 生物群系温度里的 {@code TEMPERATURE_NOISE}/{@code FROZEN_TEMPERATURE_NOISE} 是静态噪声。
 * 在周期上下文（地表生成）里把方块坐标折回，冻洋冰山的融化判定才会在副本之间一致。
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @ModifyVariable(method = "getHeightAdjustedTemperature", at = @At("HEAD"), argsOnly = true)
    private BlockPos spatialloop$periodicPosition(BlockPos pos) {
        return LoopFeatureNoise.wrap(pos);
    }
}
