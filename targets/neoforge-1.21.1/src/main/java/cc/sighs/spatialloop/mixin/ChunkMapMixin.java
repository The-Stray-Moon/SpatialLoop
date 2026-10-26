package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.geometry.LoopGeometry;
import cc.sighs.spatialloop.world.LoopLevel;
import cc.sighs.spatialloop.world.LoopStorage;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端的两件事：
 *
 * <ul>
 *   <li>构造时把循环半径写进 {@code ChunkStorage}，让区块存档坐标折回（见 {@link ChunkStorageMixin}）。</li>
 *   <li>把 {@code serverViewDistance} 压到 {@code 半径 - 1}：每个玩家的区块跟踪取
 *       {@code min(客户端请求, serverViewDistance)}，压住它，加载、发送、渲染就都跟着收窄。</li>
 * </ul>
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Inject(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/level/ChunkMap;level:Lnet/minecraft/server/level/ServerLevel;",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void spatialloop$initStorageRadius(CallbackInfo ci) {
        ((LoopStorage) (Object) this).spatialloop$setChunkRadius(LoopLevel.radiusOf(this.level));
    }

    @ModifyVariable(method = "setServerViewDistance", at = @At("HEAD"), argsOnly = true)
    private int spatialloop$clampServerViewDistance(int viewDistance) {
        int radius = LoopLevel.radiusOf(this.level);
        return radius > 0 ? Math.min(viewDistance, LoopGeometry.maxViewDistance(radius)) : viewDistance;
    }
}
