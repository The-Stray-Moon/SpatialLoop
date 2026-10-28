package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.ownership.LoopChunkOwnership;
import net.minecraft.server.level.ChunkHolder;

/** ChunkMap 所有权记录；放在 mixin 包外，避免 Mixin transformer 直接加载嵌套 mixin 类型。 */
public final class LoopChunkOwner {
    private final LoopChunkOwnership.Token token;
    private final ChunkHolder holder;
    private boolean unloaded;

    public LoopChunkOwner(LoopChunkOwnership.Token token, ChunkHolder holder) {
        this.token = token;
        this.holder = holder;
    }

    public LoopChunkOwnership.Token token() {
        return this.token;
    }

    public ChunkHolder holder() {
        return this.holder;
    }

    public boolean unloaded() {
        return this.unloaded;
    }

    public void markUnloaded() {
        this.unloaded = true;
    }
}
