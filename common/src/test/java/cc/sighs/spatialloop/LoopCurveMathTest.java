package cc.sighs.spatialloop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cc.sighs.spatialloop.geometry.LoopCurveMath;
import cc.sighs.spatialloop.geometry.LoopCurveMath.Surface;
import cc.sighs.spatialloop.geometry.LoopGeometry;
import org.junit.jupiter.api.Test;

class LoopCurveMathTest {
    private static final double EPS = 1.0e-9;

    @Test
    void noneDisablesTheBend() {
        assertEquals(1.0, LoopCurveMath.radius(Surface.NONE, 1.0, false, 32), EPS);
        assertEquals(1.0, LoopCurveMath.radius(Surface.NONE, 5.0, true, 32), EPS);
    }

    @Test
    void strengthScalesTheCurvatureAndInnerSurfaceFlipsTheSign() {
        assertEquals(256.0, LoopCurveMath.radius(Surface.OUTER_SURFACE, 1.0, false, 0), EPS);
        assertEquals(128.0, LoopCurveMath.radius(Surface.OUTER_SURFACE, 2.0, false, 0), EPS);
        assertEquals(512.0, LoopCurveMath.radius(Surface.OUTER_SURFACE, 0.5, false, 0), EPS);
        assertEquals(-256.0, LoopCurveMath.radius(Surface.INNER_SURFACE, 1.0, false, 0), EPS);
    }

    @Test
    void adaptiveUsesTheLoopPeriodAsCircumference() {
        double expected = LoopGeometry.periodBlocks(32) / (2.0 * Math.PI);
        assertEquals(expected, LoopCurveMath.radius(Surface.OUTER_SURFACE, 1.0, true, 32), EPS);
        assertEquals(expected / 2.0, LoopCurveMath.radius(Surface.OUTER_SURFACE, 2.0, true, 32), EPS);
        // 该维度没有循环时回退到默认基准半径。
        assertEquals(256.0, LoopCurveMath.radius(Surface.OUTER_SURFACE, 1.0, true, 0), EPS);
    }

    @Test
    void strengthIsClampedAndInvalidValuesFallBack() {
        assertEquals(256.0 / LoopCurveMath.MAX_STRENGTH,
                LoopCurveMath.radius(Surface.OUTER_SURFACE, 1.0e9, false, 0), EPS);
        assertEquals(256.0 / LoopCurveMath.MIN_STRENGTH,
                LoopCurveMath.radius(Surface.OUTER_SURFACE, 0.0, false, 0), EPS);
        assertEquals(256.0, LoopCurveMath.radius(Surface.OUTER_SURFACE, Double.NaN, false, 0), EPS);
    }
}
