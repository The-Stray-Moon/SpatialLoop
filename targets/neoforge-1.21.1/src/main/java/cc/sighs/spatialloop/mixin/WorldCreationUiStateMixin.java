package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.LoopRadiusHolder;
import cc.sighs.spatialloop.world.LoopLoadingLimits;
import cc.sighs.spatialloop.world.SpatialLoopChunkGenerator;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把「空间循环区块半径」挂到创建世界界面的状态对象上。
 *
 * <p>如果界面是从一个已经启用了循环的存档重建的（创造模式「重建世界」流程），
 * 就从已有维度设置里读回半径，滑条不会显示成 0。
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin implements LoopRadiusHolder {
    @Unique
    private int spatialloop$radius = LoopLoadingLimits.minimumRadius();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void spatialloop$readExistingRadius(
            Path savesFolder,
            WorldCreationContext context,
            Optional<ResourceKey<WorldPreset>> preset,
            OptionalLong seed,
            CallbackInfo ci
    ) {
        context.selectedDimensions()
                .get(LevelStem.OVERWORLD)
                .map(LevelStem::generator)
                .filter(SpatialLoopChunkGenerator.class::isInstance)
                .map(SpatialLoopChunkGenerator.class::cast)
                .ifPresent(generator -> this.spatialloop$radius = generator.radius());
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
