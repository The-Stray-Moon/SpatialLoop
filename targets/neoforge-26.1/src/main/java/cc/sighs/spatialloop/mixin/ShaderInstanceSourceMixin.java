package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.SpatialLoopShaderPatch;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 在着色器源码送进 OpenGL 之前注入球面弯折。
 *
 * <p>26.1 的 {@code ShaderManager} 只负责把资源读成「id -> 已展开 {@code #moj_import} 的 GLSL」，
 * 真正按管线 + {@code ShaderDefines} 编译发生在 {@code GlDevice#compileShader}；它在
 * {@code ShaderSource#get} 拿到源码后立刻交给 GL。因此改写点放在这次取值上：拿到的是最终源码，
 * 又能在每次（重新）编译时按当前弯折半径生成常量。
 *
 * <p>只处理 {@link SpatialLoopShaderPatch} 白名单里的世界空间顶点着色器，GUI/天空不动。
 *
 * <p>{@code GlDevice} 是包私有类，只能用 {@code targets} 点名；注入点上不需要它的类型，
 * 只用到 {@code ShaderSource}/{@code Identifier}/{@code ShaderType} 三个公开类型所以没有影子成员。
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice")
public abstract class ShaderInstanceSourceMixin {
    @Redirect(
            method = "compileShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/shaders/ShaderSource;get(Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/shaders/ShaderType;)Ljava/lang/String;"
            )
    )
    private static String spatialloop$patchShaderSource(ShaderSource shaderSource, Identifier id, ShaderType type) {
        String source = shaderSource.get(id, type);
        if (source == null || type != ShaderType.VERTEX) {
            return source;
        }
        String name = SpatialLoopShaderPatch.shaderName(id.getNamespace(), id.getPath());
        if (!SpatialLoopShaderPatch.isWorldShader(name)) {
            return source;
        }
        return SpatialLoopShaderPatch.patchVertex(name, source);
    }
}
