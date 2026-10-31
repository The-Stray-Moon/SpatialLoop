package cc.sighs.spatialloop.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;

/**
 * 把改写后的 shader 源码包装成普通的 {@link Resource}（1.18.2 版）。
 *
 * <p>1.18.2 的 {@code Resource} 是接口（{@code getLocation}/{@code getInputStream}/
 * {@code hasMetadata}/{@code getMetadata}/{@code getSourceName}/{@code close}），没有 1.20.1 的
 * {@code new Resource(source, supplier)} 构造器，只能自己转发。
 */
public final class PatchedShaderResource implements Resource {
    private final Resource delegate;
    private final byte[] source;

    public PatchedShaderResource(Resource delegate, byte[] source) {
        this.delegate = delegate;
        this.source = source;
    }

    @Override
    public ResourceLocation getLocation() {
        return this.delegate.getLocation();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(this.source);
    }

    @Override
    public boolean hasMetadata() {
        return this.delegate.hasMetadata();
    }

    @Nullable
    @Override
    public <T> T getMetadata(MetadataSectionSerializer<T> serializer) {
        return this.delegate.getMetadata(serializer);
    }

    @Override
    public String getSourceName() {
        return this.delegate.getSourceName();
    }

    @Override
    public void close() throws IOException {
        this.delegate.close();
    }
}
