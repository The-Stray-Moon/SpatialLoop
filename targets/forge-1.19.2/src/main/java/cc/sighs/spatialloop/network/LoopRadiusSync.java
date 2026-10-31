package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.spi.SpatialLoopServices;
import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 登录/换维度时把当前维度的循环半径推给客户端（1.19.2 版本）。
 *
 * <p>1.19.2 的 {@code Entity} 还没有 {@code level()} 访问器，只能通过 {@code getLevel()} 取维度；
 * 其余判断（发什么、什么时候发）与 common 版本一致。common 版本在
 * {@code prepareCommonSources} 中排除，由本文件替换。
 */
public final class LoopRadiusSync {
    private LoopRadiusSync() {
    }

    public static void send(ServerPlayer player) {
        SpatialLoopServices.platform().networking().sendLoopRadius(
                player,
                LoopLevel.radiusOf(player.getLevel())
        );
    }
}
