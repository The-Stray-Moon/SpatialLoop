package cc.sighs.spatialloop.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 1.18.2 的 {@code Screen#addRenderableWidget} 是 protected 且带交集泛型；
 * 从别的类（本模组的 {@code CreateWorldScreen} mixin）往里加滑条时用这个 invoker，
 * 避免依赖 Mixin 对继承成员的 shadow 解析。
 */
@Mixin(Screen.class)
public interface ScreenInvoker {
    @Invoker("addRenderableWidget")
    GuiEventListener spatialloop$addRenderableWidget(GuiEventListener widget);
}
