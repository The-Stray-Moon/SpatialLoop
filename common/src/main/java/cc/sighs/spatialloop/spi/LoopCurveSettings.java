package cc.sighs.spatialloop.spi;

import cc.sighs.spatialloop.geometry.LoopCurveMath;

/** 客户端球面弯折配置。文件格式与加载器配置 API 由各 target 实现。 */
public interface LoopCurveSettings {
    LoopCurveMath.Surface surface();

    double strength();

    boolean adaptive();
}
