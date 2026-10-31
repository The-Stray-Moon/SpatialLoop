package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.LoopRadiusHolder;
import cc.sighs.spatialloop.client.SpatialLoopRadiusSlider;
import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopLoadingLimits;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldGenSettingsComponent;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.18.2 的创建世界界面：主界面加一个半径滑条，点「创建新的世界」时把 overworld 的生成器换成循环生成器。
 *
 * <p>1.18.2 没有 1.19.4+ 的 {@code WorldCreationUiState}/标签页/GridLayout，界面是
 * {@code WorldGenSettingsComponent} 直接往 {@code CreateWorldScreen} 加控件。因此：
 *
 * <ul>
 *   <li>滑条挂在主界面（y = 130，位于游戏模式/命令行之间），“更多世界选项”视图里隐藏，
 *       避免和 {@code customizeTypeButton} 重叠；</li>
 *   <li>半径替换发生在 {@code onCreate} 之前，通过 {@code WorldGenSettingsComponent#updateSettings}
 *       把新的 {@code WorldGenSettings} 写回组件，随后原版流程照常编码进 level.dat；</li>
 *   <li>读档/「重建世界」时从已有 overworld 生成器读回半径。</li>
 * </ul>
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin implements LoopRadiusHolder {
    @Shadow
    @Final
    public WorldGenSettingsComponent worldGenSettingsComponent;

    @Shadow
    private boolean worldGenSettingsVisible;

    @Shadow
    public boolean hardCore;

    @Unique
    private int spatialloop$radius = LoopLoadingLimits.defaultRadius();

    @Unique
    private SpatialLoopRadiusSlider spatialloop$slider;

    @Inject(method = "init", at = @At("RETURN"))
    private void spatialloop$addRadiusSlider(CallbackInfo ci) {
        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        this.spatialloop$readRadius();
        this.spatialloop$slider = new SpatialLoopRadiusSlider(
                self.width / 2 - 155, 130, 310, 20, this);
        this.spatialloop$slider.visible = !this.worldGenSettingsVisible;
        ((ScreenInvoker) self).spatialloop$addRenderableWidget(this.spatialloop$slider);
    }

    @Unique
    private void spatialloop$readRadius() {
        ChunkGenerator generator = this.worldGenSettingsComponent.makeSettings(false).overworld();
        if (generator instanceof SpatialLoopChunkGenerator loop) {
            this.spatialloop$radius = loop.radius();
        }
    }

    /** 切到「更多世界选项」时隐藏滑条，和原版那一排控件的可见性保持一致。 */
    @Inject(method = "setWorldGenSettingsVisible", at = @At("RETURN"))
    private void spatialloop$syncSliderVisibility(boolean visible, CallbackInfo ci) {
        if (this.spatialloop$slider != null) {
            this.spatialloop$slider.visible = !visible;
        }
    }

    /**
     * 把 overworld 换成循环生成器。超平坦/调试世界的生成器不是 {@code NoiseBasedChunkGenerator}，
     * 保持玩家选择不变；调试世界直接跳过。
     */
    @Inject(method = "onCreate", at = @At("HEAD"))
    private void spatialloop$installOverworldGenerator(CallbackInfo ci) {
        int radius = this.spatialloop$radius;
        if (!LoopGeometry.isEnabled(radius) || this.worldGenSettingsComponent.isDebug()) {
            return;
        }
        WorldGenSettings current = this.worldGenSettingsComponent.makeSettings(this.hardCore);
        if (!(current.overworld() instanceof NoiseBasedChunkGenerator generator)) {
            return;
        }
        RegistryAccess registryAccess = this.worldGenSettingsComponent.registryHolder();
        SpatialLoopChunkGenerator periodic = SpatialLoopChunkGenerator.create(
                registryAccess.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY),
                registryAccess.registryOrThrow(Registry.NOISE_REGISTRY),
                generator.getBiomeSource(),
                current.seed(),
                ((NoiseBasedChunkGeneratorAccessor) generator).spatialloop$settings(),
                radius
        );
        Registry<LevelStem> dimensions = WorldGenSettings.withOverworld(
                registryAccess.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY),
                current.dimensions(),
                periodic
        );
        this.worldGenSettingsComponent.updateSettings(new WorldGenSettings(
                current.seed(),
                current.generateFeatures(),
                current.generateBonusChest(),
                dimensions,
                current.legacyCustomOptions
        ));
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
