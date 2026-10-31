package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.LoopRadiusHolder;
import cc.sighs.spatialloop.client.SpatialLoopRadiusSlider;
import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopLoadingLimits;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldGenSettingsComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 创建世界界面里的「空间循环区块半径」（1.19.2 版本）。
 *
 * <p>1.19.2 还没有 {@code WorldCreationUiState}/{@code WorldTab}：世界选项由
 * {@link WorldGenSettingsComponent} 持有，界面在它的 {@code init} 里搭建，
 * 最终设置由 {@code createFinalSettings} 产出。因此：
 *
 * <ul>
 *   <li>构造时从已有维度设置里读回半径（「重建世界」流程滑条不会显示成 0）；</li>
 *   <li>{@code init} 结束时把滑条加进创建界面右侧空位，并随 {@code setVisibility} 显隐；</li>
 *   <li>{@code createFinalSettings} 返回前把 overworld 的生成器换成循环生成器，半径因此随
 *       level.dat 保存，读档时由生成器 codec 原样还原。</li>
 * </ul>
 *
 * <p>超平坦/调试世界的生成器不是 {@link NoiseBasedChunkGenerator}，保持玩家选择不变。
 */
@Mixin(WorldGenSettingsComponent.class)
public abstract class WorldGenSettingsComponentMixin implements LoopRadiusHolder {
    @Shadow
    private WorldCreationContext settings;

    @Unique
    private int spatialloop$radius = LoopLoadingLimits.defaultRadius();

    @Unique
    private SpatialLoopRadiusSlider spatialloop$slider;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void spatialloop$readExistingRadius(
            WorldCreationContext context,
            Optional<ResourceKey<WorldPreset>> preset,
            OptionalLong seed,
            CallbackInfo ci
    ) {
        LevelStem stem = context.worldGenSettings().dimensions().get(LevelStem.OVERWORLD);
        if (stem != null && stem.generator() instanceof SpatialLoopChunkGenerator generator) {
            this.spatialloop$radius = generator.radius();
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void spatialloop$addRadiusSlider(CreateWorldScreen screen, Minecraft minecraft, Font font, CallbackInfo ci) {
        SpatialLoopRadiusSlider slider = new SpatialLoopRadiusSlider(
                screen.width / 2 + 5, 185, 150, 20, this);
        slider.visible = !this.spatialloop$isDebug();
        this.spatialloop$slider = slider;
        ((ScreenAccessor) screen).spatialloop$addRenderableWidget(slider);
    }

    @Inject(method = "setVisibility", at = @At("TAIL"))
    private void spatialloop$updateSliderVisibility(boolean visible, CallbackInfo ci) {
        if (this.spatialloop$slider != null) {
            this.spatialloop$slider.visible = visible && !this.spatialloop$isDebug();
        }
    }

    @Inject(method = "createFinalSettings", at = @At("RETURN"), cancellable = true)
    private void spatialloop$installOverworldGenerator(
            boolean hardCore,
            CallbackInfoReturnable<WorldCreationContext> cir
    ) {
        int radius = this.spatialloop$radius;
        if (!LoopGeometry.isEnabled(radius) || this.spatialloop$isDebug()) {
            return;
        }
        WorldCreationContext context = cir.getReturnValue();
        cir.setReturnValue(context.withSettings(
                (registry, worldGenSettings) -> this.spatialloop$replaceOverworld(registry, worldGenSettings, radius)
        ));
    }

    @Unique
    private WorldGenSettings spatialloop$replaceOverworld(
            RegistryAccess.Frozen registry,
            WorldGenSettings worldGenSettings,
            int radius
    ) {
        LevelStem stem = worldGenSettings.dimensions().get(LevelStem.OVERWORLD);
        if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator generator)) {
            return worldGenSettings;
        }
        Registry<StructureSet> structureSets = registry.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
        Registry<NormalNoise.NoiseParameters> noises = registry.registryOrThrow(Registry.NOISE_REGISTRY);
        SpatialLoopChunkGenerator periodic = new SpatialLoopChunkGenerator(
                structureSets,
                noises,
                generator.getBiomeSource(),
                generator.generatorSettings(),
                radius
        );
        return WorldGenSettings.replaceOverworldGenerator(registry, worldGenSettings, periodic);
    }

    @Unique
    private boolean spatialloop$isDebug() {
        return this.settings != null && this.settings.worldGenSettings().isDebug();
    }

    @Override
    public int spatialloop$getRadius() {
        return this.spatialloop$radius;
    }

    @Override
    public void spatialloop$setRadius(int radius) {
        this.spatialloop$radius = radius;
    }
}
