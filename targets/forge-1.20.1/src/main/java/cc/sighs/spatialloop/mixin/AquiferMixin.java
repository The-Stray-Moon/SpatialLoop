package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopPositionalRandomFactory;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 含水层的两处噪声用<b>派生坐标</b>采样：{@code fluid_level_spread} 用 16×40×16 的格子坐标，
 * {@code lava} 用 64×40×64 的格子坐标。{@code DensityFunctions.Noise} 只知道「输入乘了 xzScale」，
 * 不知道输入其实已经被除过 16/64；单靠输入周期会算错。
 *
 * <p>这里在入口先把方块坐标折回一个周期，派生出来的格子坐标自然也在副本之间相同。
 * 周期从含水层持有的 {@link LoopPositionalRandomFactory} 反推（它的周期是「格子」，乘 16 得到方块）。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public abstract class AquiferMixin {
    @Shadow
    @Final
    private PositionalRandomFactory positionalRandomFactory;

    @ModifyVariable(method = "computeRandomizedFluidSurfaceLevel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int spatialloop$wrapFluidLevelX(int blockX) {
        return this.spatialloop$wrap(blockX);
    }

    @ModifyVariable(method = "computeRandomizedFluidSurfaceLevel", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int spatialloop$wrapFluidLevelZ(int blockZ) {
        return this.spatialloop$wrap(blockZ);
    }

    @ModifyVariable(method = "computeFluidType", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int spatialloop$wrapFluidTypeX(int blockX) {
        return this.spatialloop$wrap(blockX);
    }

    @ModifyVariable(method = "computeFluidType", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int spatialloop$wrapFluidTypeZ(int blockZ) {
        return this.spatialloop$wrap(blockZ);
    }

    @Unique
    private int spatialloop$wrap(int block) {
        int period = this.positionalRandomFactory instanceof LoopPositionalRandomFactory factory
                ? factory.xzPeriod() * 16
                : 0;
        if (period <= 0) {
            return block;
        }
        return Math.floorMod(block + period / 2, period) - period / 2;
    }
}
