package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.LoopRadiusHolder;
import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 点「创建新的世界」时，把 overworld 的生成器换成循环生成器。
 *
 * <p>替换发生在 {@code onCreate} 的第一行：此时界面状态尚未被烘焙成 {@code WorldDimensions}，
 * 换成自定义生成器后它会随 level.dat 一起保存，读档时由生成器的 codec 原样还原，
 * 半径因此是「存档属性」而不是配置文件。
 *
 * <p>地形本身用原版 {@code NoiseBasedChunkGenerator} 的生物群系源与噪声设置，只是把生成器换成
 * {@link SpatialLoopChunkGenerator}（多一个半径）。超平坦/调试世界的生成器不是
 * {@code NoiseBasedChunkGenerator}，因此保持玩家选择不变。
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @Shadow
    @Final
    WorldCreationUiState uiState;

    @Inject(method = "onCreate", at = @At("HEAD"))
    private void spatialloop$installOverworldGenerator(CallbackInfo ci) {
        int radius = ((LoopRadiusHolder) (Object) this.uiState).spatialloop$getRadius();
        if (!LoopGeometry.isEnabled(radius) || this.uiState.isDebug()) {
            return;
        }
        this.uiState.updateDimensions(
                (registry, dimensions) -> this.spatialloop$replaceOverworld(registry, dimensions, radius)
        );
    }

    @Unique
    private WorldDimensions spatialloop$replaceOverworld(
            RegistryAccess.Frozen registry,
            WorldDimensions dimensions,
            int radius
    ) {
        LevelStem stem = dimensions.get(LevelStem.OVERWORLD).orElse(null);
        if (stem == null) {
            return dimensions;
        }
        if (!(stem.generator() instanceof NoiseBasedChunkGenerator generator)) {
            return dimensions;
        }
        SpatialLoopChunkGenerator periodic = new SpatialLoopChunkGenerator(
                generator.getBiomeSource(),
                generator.generatorSettings(),
                radius
        );
        return dimensions.replaceOverworldGenerator(registry, periodic);
    }
}
