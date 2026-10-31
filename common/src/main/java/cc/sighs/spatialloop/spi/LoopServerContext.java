package cc.sighs.spatialloop.spi;

/**
 * 当前服务端进程的上下文。
 *
 * <p>获取「当前服务器」是加载器差异（NeoForge 用 {@code ServerLifecycleHooks}，Forge/Fabric
 * 各有自己的入口），由 target 实现；common 只用它做视距钳制。
 */
public interface LoopServerContext {
    /** 当前服务器主世界的循环区块半径；服务端未启动时返回 0。 */
    int overworldLoopRadius();
}
