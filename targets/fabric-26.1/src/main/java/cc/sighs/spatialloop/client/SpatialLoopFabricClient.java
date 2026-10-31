package cc.sighs.spatialloop.client;

import cc.sighs.spatialloop.network.LoopRadiusPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Fabric 客户端入口：只注册半径同步的接收端。 */
public final class SpatialLoopFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(LoopRadiusPayload.TYPE, (payload, context) ->
                context.client().execute(() -> SpatialLoopCurve.setServerLoopRadius(payload.radius())));
    }
}
