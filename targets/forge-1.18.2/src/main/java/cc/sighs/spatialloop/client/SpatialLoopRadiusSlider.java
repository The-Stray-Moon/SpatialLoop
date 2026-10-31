package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopLoadingLimits;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/**
 * 创建世界界面里的「空间循环区块半径」滑条。0 表示关闭，其余值即区块半径。
 *
 * <p>拖动只会改变界面状态；真正的世界生成器是在点「创建新的世界」时
 * （{@code WorldGenSettingsComponent#makeSettings} 的返回值）按这个值装配进 overworld 的
 * {@code LevelStem}，随后被 level.dat 原样保存。
 *
 * <p>1.18.2 没有 {@code Tooltip}（1.19+），所以去掉了悬停说明；文本与其它版本一致。
 */
public class SpatialLoopRadiusSlider extends AbstractSliderButton {
    private final LoopRadiusHolder holder;

    public SpatialLoopRadiusSlider(int x, int y, int width, int height, LoopRadiusHolder holder) {
        super(x, y, width, height, new TextComponent(""), holder.spatialloop$getRadius() / (double) LoopGeometry.MAX_RADIUS);
        this.holder = holder;
        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        int raw = (int) Math.round(this.value * LoopGeometry.MAX_RADIUS);
        Component valueText;
        if (raw <= 0) {
            valueText = new TranslatableComponent("spatialloop.create.radius.off");
        } else {
            valueText = new TranslatableComponent(
                    "spatialloop.create.radius.value",
                    raw,
                    LoopGeometry.periodBlocks(raw),
                    LoopLoadingLimits.maxDistance(raw)
            );
        }
        this.setMessage(new TranslatableComponent("spatialloop.create.radius", valueText));
    }

    @Override
    protected void applyValue() {
        int raw = (int) Math.round(this.value * LoopGeometry.MAX_RADIUS);
        int effective = raw <= 0 ? 0 : Math.max(LoopLoadingLimits.minimumRadius(), raw);
        this.holder.spatialloop$setRadius(effective);
        if (effective != raw) {
            // 只在用户选择新值时吸附，不改写已有存档半径。
            this.value = effective / (double) LoopGeometry.MAX_RADIUS;
        }
        this.updateMessage();
    }
}
