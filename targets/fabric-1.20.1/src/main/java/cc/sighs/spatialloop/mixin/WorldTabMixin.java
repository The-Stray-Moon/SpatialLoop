package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.LoopRadiusHolder;
import cc.sighs.spatialloop.client.SpatialLoopRadiusSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 在「世界」选项卡里插入半径滑条。
 *
 * <p>原版构造选项卡时先创建 {@code RowHelper} 再往里加控件；这里抓住那个局部变量，
 * 在第一个控件之前加一整行的滑条，因此它出现在「世界类型」上方。
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin {
    @Unique
    private static final int SPATIALLOOP$TAB_WIDTH = 310;

    @ModifyVariable(method = "<init>", at = @At("STORE"), ordinal = 0)
    private GridLayout.RowHelper spatialloop$addRadiusSlider(GridLayout.RowHelper rowHelper) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof CreateWorldScreen screen) {
            rowHelper.addChild(
                    new SpatialLoopRadiusSlider(0, 0, SPATIALLOOP$TAB_WIDTH, 20, (LoopRadiusHolder) (Object) screen.getUiState()),
                    2
            );
        }
        return rowHelper;
    }
}
