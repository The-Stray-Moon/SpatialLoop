package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopBiomeManager;
import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 区块生成用的 {@code WorldGenRegion} 会另外新建一个 {@code BiomeManager}（种子还是混淆过的），
 * feature 的 {@code BiomeFilter}、地表规则读的是它，不是维度那个。这里同样盖上周期。
 */
@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    @Final
    private BiomeManager biomeManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void spatialloop$installPeriod(CallbackInfo ci) {
        int radius = LoopLevel.radiusOf(this.level);
        if (radius > 0) {
            ((LoopBiomeManager) (Object) this.biomeManager)
                    .spatialloop$periodBlocks(LoopGeometry.periodBlocks(radius));
        }
    }
}
