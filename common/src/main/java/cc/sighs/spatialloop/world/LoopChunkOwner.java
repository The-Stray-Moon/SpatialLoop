package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.ownership.LoopChunkOwnership;
import net.minecraft.server.level.ChunkHolder;

/** ChunkMap 所有权记录；放在 mixin 包外，避免 Mixin transformer 直接加载嵌套 mixin 类型。 */
public final class LoopChunkOwner {
    private final LoopChunkOwnership.Token token;
    private ChunkHolder holder;
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

    /**
     * 同一个绝对坐标在旧 holder 卸载后又被重新 ticket（例如出生区 START 票据释放后玩家立即登录）。
     * 这不改变 canonical 归属，只需要把记录指到新 holder 并撤销“已卸载”标记。
     */
    public void rebind(ChunkHolder holder) {
        this.holder = holder;
        this.unloaded = false;
    }
}
