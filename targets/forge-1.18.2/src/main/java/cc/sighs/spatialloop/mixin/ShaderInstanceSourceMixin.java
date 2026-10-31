package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.PatchedShaderResource;
import cc.sighs.spatialloop.client.SpatialLoopShaderPatch;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 在 {@code ShaderInstance} 读取资源时注入球面弯折（1.18.2 版）。
 *
 * <p>1.18.2 的 {@code ResourceProvider} 只有 {@code getResource(ResourceLocation)}：
 * {@code ShaderInstance} 构造器用它读 shader JSON，GLSL 源码则在私有静态 {@code getOrCreate}
 * 里经同一个方法取 {@code Resource} 后交给 {@code Program.compileShader}。
 * JSON 补 {@code ViewRot}/{@code CurveRadius} 两个 uniform；顶点源码替换位置计算。
 * 只处理 {@link SpatialLoopShaderPatch} 白名单里的世界空间着色器，GUI/天空不动。
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceSourceMixin {
    @Redirect(
            method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceProvider;getResource(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/Resource;"
            )
    )
    private static Resource spatialloop$patchShaderJson(ResourceProvider provider, ResourceLocation location)
            throws IOException {
        Resource original = provider.getResource(location);
        String name = SpatialLoopShaderPatch.shaderName(location.getNamespace(), location.getPath());
        if (!SpatialLoopShaderPatch.isWorldShader(name)) {
            return original;
        }
        String json;
        try (InputStream input = original.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            json = reader.lines().collect(Collectors.joining("\n"));
        }
        return new PatchedShaderResource(
                original,
                SpatialLoopShaderPatch.patchJson(json).getBytes(StandardCharsets.UTF_8)
        );
    }

    @Redirect(
            method = "getOrCreate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceProvider;getResource(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/Resource;"
            )
    )
    private static Resource spatialloop$patchShaderSource(ResourceProvider provider, ResourceLocation location)
            throws IOException {
        Resource original = provider.getResource(location);
        String name = SpatialLoopShaderPatch.shaderName(location.getNamespace(), location.getPath());
        if (!SpatialLoopShaderPatch.isWorldShader(name) || !location.getPath().endsWith(".vsh")) {
            return original;
        }
        try {
            String source;
            try (InputStream input = original.getInputStream()) {
                source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            String patched = SpatialLoopShaderPatch.patchVertex(name, source);
            if (patched.equals(source)) {
                return original;
            }
            return new PatchedShaderResource(original, patched.getBytes(StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
