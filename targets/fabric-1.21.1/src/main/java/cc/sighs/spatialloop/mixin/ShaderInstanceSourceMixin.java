package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.client.SpatialLoopShaderPatch;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
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
 * 在 {@code ShaderInstance} 读取资源时注入球面弯折。
 *
 * <p>JSON 补 {@code ViewRot}/{@code CurveRadius} 两个 uniform；顶点源码替换位置计算。
 * 只处理 {@link SpatialLoopShaderPatch} 白名单里的世界空间着色器，GUI/天空不动。
 */
@Mixin(ShaderInstance.class)
public abstract class ShaderInstanceSourceMixin {
    @Redirect(
            method = "<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;Ljava/lang/String;Lcom/mojang/blaze3d/vertex/VertexFormat;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceProvider;openAsReader(Lnet/minecraft/resources/ResourceLocation;)Ljava/io/BufferedReader;"
            )
    )
    private static BufferedReader spatialloop$patchShaderJson(ResourceProvider provider, ResourceLocation location)
            throws IOException {
        BufferedReader original = provider.openAsReader(location);
        String name = SpatialLoopShaderPatch.shaderName(location.getNamespace(), location.getPath());
        if (!SpatialLoopShaderPatch.isWorldShader(name)) {
            return original;
        }
        String json;
        try (BufferedReader reader = original) {
            json = reader.lines().collect(Collectors.joining("\n"));
        }
        return new BufferedReader(new StringReader(SpatialLoopShaderPatch.patchJson(json)));
    }

    @Redirect(
            method = "getOrCreate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceProvider;getResourceOrThrow(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/Resource;"
            )
    )
    private static Resource spatialloop$patchShaderSource(ResourceProvider provider, ResourceLocation location)
            throws FileNotFoundException {
        Resource original = provider.getResourceOrThrow(location);
        String name = SpatialLoopShaderPatch.shaderName(location.getNamespace(), location.getPath());
        if (!SpatialLoopShaderPatch.isWorldShader(name) || !location.getPath().endsWith(".vsh")) {
            return original;
        }
        try {
            String source;
            try (InputStream input = original.open()) {
                source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            String patched = SpatialLoopShaderPatch.patchVertex(name, source);
            if (patched.equals(source)) {
                return original;
            }
            byte[] bytes = patched.getBytes(StandardCharsets.UTF_8);
            return new Resource(original.source(), () -> new ByteArrayInputStream(bytes));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
