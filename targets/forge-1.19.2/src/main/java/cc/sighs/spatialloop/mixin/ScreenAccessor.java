package cc.sighs.spatialloop.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把 {@code Screen#addRenderableWidget}（protected）暴露给创建世界界面的 mixin。
 *
 * <p>1.19.2 还没有 1.20.1 的 {@code GridLayout}/{@code WorldCreationUiState}，滑条只能自己加进
 * {@code CreateWorldScreen}；mixin 与该类不在同一个包，无法直接调用 protected 方法。
 *
 * <p>泛型 {@code <T extends GuiEventListener & Widget & NarratableEntry> T addRenderableWidget(T)}
 * 的擦除类型是最左边界 {@link GuiEventListener}，因此 invoker 用擦除后的签名声明。
 */
@Mixin(Screen.class)
public interface ScreenAccessor {
    @Invoker("addRenderableWidget")
    GuiEventListener spatialloop$addRenderableWidget(GuiEventListener widget);
}
