package cc.sighs.spatialloop.world;

import cc.sighs.spatialloop.spi.SpatialLoopServices;

/**
 * 强制视距：服务端负责真正少发区块，客户端负责把渲染/雾距离一起收窄到同一个值。
 *
 * <p>原版 {@code Options.getEffectiveRenderDistance()} 取「客户端设置」与「服务端登录时告知的
 * chunkRadius」的较小值；这里用与 {@code ChunkMap#setServerViewDistance} 完全相同的
 * {@link LoopLoadingLimits#maxDistance(int)}，避免客户端按 {@code 半径 - 1} 渲染、
 * 服务端却只能发更小范围，从而出现大片空洞。
 *
 * <p>「当前服务器是不是循环维度」由 target 的 {@code LoopServerContext} 回答，本类不引用加载器 API。
 */
public final class SpatialLoopViewDistance {
    private SpatialLoopViewDistance() {
    }

    public static int clamp(int requested) {
        int radius = SpatialLoopServices.platform().serverContext().overworldLoopRadius();
        if (radius <= 0) {
            return requested;
        }
        return Math.min(requested, LoopLoadingLimits.maxDistance(radius));
    }
}
