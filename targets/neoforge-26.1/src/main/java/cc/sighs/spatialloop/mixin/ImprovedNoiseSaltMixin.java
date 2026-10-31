package cc.sighs.spatialloop.mixin;

import cc.sighs.spatialloop.world.LoopNoise;
import cc.sighs.spatialloop.world.LoopNoiseSalt;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 缓存 {@link LoopNoise} 用的梯度盐：每层八度的 {@code xo/yo/zo} 在构造后不变，
 * 而地形生成会把这个值算上百万次。volatile 只是为了跨 worldgen 线程可见；盐是确定值，
 * 并发重复计算也只会得到同一个结果。
 */
@Mixin(ImprovedNoise.class)
public abstract class ImprovedNoiseSaltMixin implements LoopNoiseSalt {
    @Unique
    private volatile long spatialloop$salt = Long.MIN_VALUE;

    @Override
    public long spatialloop$salt() {
        long salt = this.spatialloop$salt;
        if (salt == Long.MIN_VALUE) {
            ImprovedNoise self = (ImprovedNoise) (Object) this;
            salt = LoopNoise.hashSalt(self.xo, self.yo, self.zo);
            this.spatialloop$salt = salt;
        }
        return salt;
    }
}
