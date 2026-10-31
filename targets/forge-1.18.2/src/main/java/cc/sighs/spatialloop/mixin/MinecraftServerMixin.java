package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 出生点区块不再常驻。
 *
 * <p>原版用 START 票据把出生点周围约 11 个区块永久保持加载。循环世界里这会和同余副本撞车：
 * 玩家走到出生区的副本时，两份区块会同时存在于内存里，保存时后写覆盖先写，方块改动会丢。
 * 启动准备阶段结束后释放这张票据，出生区就按普通区块处理——正常游玩时视距/模拟距离都小于周期，
 * 不会有两份同余区块同时加载。
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "prepareLevels", at = @At("RETURN"))
    private void spatialloop$releaseSpawnChunks(ChunkProgressListener listener, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        for (ServerLevel level : server.getAllLevels()) {
            if (LoopLevel.radiusOf(level) <= 0) {
                continue;
            }
            // 1.18.2 没有 spawnChunkRadius 游戏规则：原版 prepareLevels 固定用半径 11 给主世界加
            // START 票据，释放时必须用同一个半径。其它维度上没有这张票据，删除是空操作。
            level.getChunkSource()
                    .removeRegionTicket(TicketType.START, new ChunkPos(level.getSharedSpawnPos()), 11, Unit.INSTANCE);
        }
    }
}
