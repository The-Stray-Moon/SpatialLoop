package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.SpatialLoopCurve;
import com.mojang.blaze3d.shaders.AbstractUniform;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给带 {@code CurveRadius} 的地形着色器上传弯折半径。
 *
 * <p>{@code apply()} 会在上传 sampler 之后统一 upload 所有 uniform，所以在 HEAD 写入值即可；
 * 只对 JSON 里声明了该 uniform 的着色器生效，其它着色器 {@code safeGetUniform} 返回 null。
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceCurveMixin {
    @Inject(method = "apply", at = @At("HEAD"))
    private void spatialloop$uploadCurveRadius(CallbackInfo ci) {
        AbstractUniform uniform = ((ShaderInstance) (Object) this).safeGetUniform("CurveRadius");
        if (uniform != null) {
            uniform.set(SpatialLoopCurve.radius());
        }
        AbstractUniform viewRot = ((ShaderInstance) (Object) this).safeGetUniform("ViewRot");
        if (viewRot != null) {
            viewRot.set(SpatialLoopCurve.viewRotation());
        }
    }
}
