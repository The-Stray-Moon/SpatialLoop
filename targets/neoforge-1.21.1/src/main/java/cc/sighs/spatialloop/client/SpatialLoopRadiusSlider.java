package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * 创建世界界面里的「空间循环区块半径」滑条。0 表示关闭，其余值即区块半径。
 *
 * <p>拖动只会改变界面状态；真正的世界生成器是在点「创建新的世界」时（{@code CreateWorldScreen#onCreate}）
 * 按这个值装配进 overworld 的 {@code LevelStem}，随后被 level.dat 原样保存。
 */
public class SpatialLoopRadiusSlider extends AbstractSliderButton {
    private final LoopRadiusHolder holder;

    public SpatialLoopRadiusSlider(int x, int y, int width, int height, LoopRadiusHolder holder) {
        super(x, y, width, height, Component.empty(), holder.spatialloop$getRadius() / (double) LoopGeometry.MAX_RADIUS);
        this.holder = holder;
        this.setTooltip(Tooltip.create(Component.translatable("spatialloop.create.radius.tooltip")));
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        int raw = (int) Math.round(this.value * LoopGeometry.MAX_RADIUS);
        Component valueText;
        if (raw <= 0) {
            valueText = Component.translatable("spatialloop.create.radius.off");
        } else {
            int radius = LoopGeometry.sanitizeRadius(raw);
            valueText = Component.translatable("spatialloop.create.radius.value", radius, LoopGeometry.periodBlocks(radius));
        }
        this.setMessage(Component.translatable("spatialloop.create.radius", valueText));
    }

    @Override
    protected void applyValue() {
        int raw = (int) Math.round(this.value * LoopGeometry.MAX_RADIUS);
        int effective = LoopGeometry.sanitizeRadius(raw);
        this.holder.spatialloop$setRadius(effective);
        if (effective != raw) {
            // 1、2 区块半径放不下视距，吸附到可用最小值。
            this.value = effective / (double) LoopGeometry.MAX_RADIUS;
        }
        this.updateMessage();
    }
}
