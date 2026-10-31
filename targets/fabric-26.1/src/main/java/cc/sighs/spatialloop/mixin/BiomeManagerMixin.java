package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopBiomeManager;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 生物群系的模糊查找按周期折回。
 *
 * <p>{@code getBiome} 会取目标位置附近的 8 个 quart 格，用绝对坐标哈希算距离，挑最近的一个。
 * 副本之间哈希不同，群系边界上就会差一格（feature 的 {@code BiomeFilter}、地表规则、刷怪都受影响）。
 *
 * <p>只折<b>哈希输入</b>（quart 坐标），不折查询位置：查询位置必须留在当前 {@code WorldGenRegion}
 * 内，否则会去取区域外的区块。生物群系数据本身已经是周期的，所以选中的候选格结果也一致。
 */
@Mixin(BiomeManager.class)
public abstract class BiomeManagerMixin implements LoopBiomeManager {
    @Shadow
    private static double getFiddledDistance(long seed, int x, int y, int z, double dx, double dy, double dz) {
        throw new AssertionError();
    }

    @Unique
    private int spatialloop$periodBlocks;

    @Redirect(
            method = "getBiome",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/biome/BiomeManager;getFiddledDistance(JIIIDDD)D"
            )
    )
    private double spatialloop$periodicDistance(long seed, int x, int y, int z, double dx, double dy, double dz) {
        int period = this.spatialloop$periodBlocks;
        if (period <= 0) {
            return getFiddledDistance(seed, x, y, z, dx, dy, dz);
        }
        int quartPeriod = Math.max(1, period / 4);
        return getFiddledDistance(
                seed,
                Math.floorMod(x, quartPeriod),
                y,
                Math.floorMod(z, quartPeriod),
                dx,
                dy,
                dz
        );
    }

    @Inject(method = "withDifferentSource", at = @At("RETURN"))
    private void spatialloop$copyPeriod(
            BiomeManager.NoiseBiomeSource source,
            CallbackInfoReturnable<BiomeManager> cir
    ) {
        ((LoopBiomeManager) (Object) cir.getReturnValue()).spatialloop$periodBlocks(this.spatialloop$periodBlocks);
    }

    @Override
    public int spatialloop$periodBlocks() {
        return this.spatialloop$periodBlocks;
    }

    @Override
    public void spatialloop$periodBlocks(int periodBlocks) {
        this.spatialloop$periodBlocks = periodBlocks;
    }
}
