package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.spi.SpatialLoopServices;
import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 登录/换维度时把当前维度的循环半径推给客户端（1.18.2 版）。
 *
 * <p>1.18.2 还没有 {@code Entity#level()}，取维度只能用 {@code ServerPlayer#getLevel()}。
 * 「取哪个半径」和「什么时候发」仍与其它版本一致。
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
