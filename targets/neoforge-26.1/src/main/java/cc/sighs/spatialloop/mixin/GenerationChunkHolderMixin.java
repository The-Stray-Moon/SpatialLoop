package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopGenerationAdmission;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GenerationChunkHolder.class)
public abstract class GenerationChunkHolderMixin {
    @Inject(method = "rescheduleChunkTask", at = @At("HEAD"), cancellable = true)
    private void spatialloop$admitDependencies(ChunkMap map, ChunkStatus status, CallbackInfo ci) {
        // null 是原版取消请求，必须立即放行，才能释放旧任务的 generation 引用。
        if (status != null && !((LoopGenerationAdmission) map).spatialloop$admit(
                (GenerationChunkHolder) (Object) this, status)) {
            ci.cancel();
        }
    }
}
