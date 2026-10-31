package cc.sighs.spatialloop.network;

import cc.sighs.spatialloop.spi.SpatialLoopServices;
import cc.sighs.spatialloop.world.LoopLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 登录/换维度时把当前维度的循环半径推给客户端。
 *
 * <p>「取哪个半径」和「什么时候发」是共同逻辑；具体怎么发（payload 类型、注册 API）
 * 由 target 的 {@code LoopNetworking} 实现。
 */
public final class LoopRadiusSync {
    private LoopRadiusSync() {
    }

    public static void send(ServerPlayer player) {
        SpatialLoopServices.platform().networking().sendLoopRadius(
                player,
                LoopLevel.radiusOf(player.level())
        );
    }
}
