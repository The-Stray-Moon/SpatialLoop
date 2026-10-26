package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 强制视距：服务端负责真正少发区块，客户端负责把渲染/雾距离一起收窄。
 *
 * <p>原版 {@code Options.getEffectiveRenderDistance()} 取「客户端设置」与「服务端登录时告知的
 * chunkRadius」的较小值，所以只要把登录包与视距变更包里的半径压到 {@code 半径 - 1}，
 * 客户端的雾会自然收进来；服务端再用 {@code ChunkMap#setServerViewDistance} 的同一上限限制加载与发送。
 */
public final class SpatialLoopViewDistance {
    private SpatialLoopViewDistance() {
    }

    public static int clamp(int requested) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return requested;
        }
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            return requested;
        }
        int radius = LoopLevel.radiusOf(overworld);
        if (radius <= 0) {
            return requested;
        }
        return Math.min(requested, LoopGeometry.maxViewDistance(radius));
    }
}
