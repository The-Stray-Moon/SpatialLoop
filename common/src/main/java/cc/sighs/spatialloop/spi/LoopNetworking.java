package cc.sighs.spatialloop.spi;

import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端 → 客户端的循环半径同步。
 *
 * <p>载荷格式和注册 API 是加载器差异，由各 target 的 payload 实现承担；common 只负责
 * 「什么时候、发什么值」。
 */
public interface LoopNetworking {
    void sendLoopRadius(ServerPlayer player, int radius);
}
