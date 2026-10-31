package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.network.LoopRadiusPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Fabric 客户端入口：只注册半径同步的接收端。 */
public final class SpatialLoopFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LoopRadiusPayload.CHANNEL,
                (client, handler, buf, responseSender) -> {
                    // 1.20.1 的原始载荷处理器没有脱线程的 Packet 封装，Buff 只能在网络线程读完，
                    // 游戏状态更新交回客户端主线程。
                    int radius = LoopRadiusPayload.read(buf).radius();
                    client.execute(() -> SpatialLoopCurve.setServerLoopRadius(radius));
                });
    }
}
