package cc.sighs.spatialloop.client;

/** 由 {@code WorldCreationUiState} 的 mixin 实现：创建世界界面里的「空间循环区块半径」。 */
public interface LoopRadiusHolder {
    int spatialloop$getRadius();

    void spatialloop$setRadius(int radius);
}
