package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.SpatialLoopShaderPatch;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让已编译的世界空间着色器跟上当前弯折半径。
 *
 * <p>26.1 的着色器没有可写的自定义 uniform（{@code GlProgram} 只认管线声明过的 block 和四个内置
 * block），弯折半径由 {@code ShaderInstanceSourceMixin} 在编译期以 {@code const} 常量烘进源码。
 * 半径改变时（改客户端配置、登录/换维度收到服务端推送的循环半径）在帧首清空管线缓存：编译产物
 * 全部作废，下一次使用会按新常量重新编译。清空放在帧首，避免删掉本帧正在执行的 program。
 */
@Mixin(GameRenderer.class)
public abstract class ShaderInstanceCurveMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void spatialloop$refreshCurveRadius(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (SpatialLoopShaderPatch.takeRecompileRequest()) {
            RenderSystem.getDevice().clearPipelineCache();
        }
    }
}
