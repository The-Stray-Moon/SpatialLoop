package cc.sighs.spatialloop.world;

/**
 * 由 {@code ImprovedNoise} 的 mixin 实现：缓存由 {@code xo/yo/zo} 派生的梯度盐。
 *
 * <p>{@link LoopNoise} 每层八度都要用到这个盐；不缓存的话每次采样都要做 3 次 mix64。
 * 盐只取决于构造后就不再变化的三个偏移量，所以可以安全地算一次。
 */
public interface LoopNoiseSalt {
    long spatialloop$salt();
}
