package cc.sighs.spatialloop.world;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 视距上限不是拍脑袋的余量：{@code ChunkLevel.MAX_LEVEL} 比玩家加载票据等级
 * （ENTITY_TICKING = 31）多保留 13 格 holder；两倍持有半径必须小于一个周期。
 *
 * <p>{@code ChunkStatus}/{@code ChunkLevel} 的静态初始化需要注册表 bootstrap，单元测试里读不到，
 * 所以「13 = MAX_LEVEL - 31」与「生成依赖半径 = 11」由 {@code ChunkMapOwnershipMixin} 在真实
 * 服务端构造维度时用 {@code LoopGenerationReach.forStatus(FULL)} 现场核对；这里钉住公式结果。
 */
class LoopLoadingLimitsTest {

    @Test
    void limitsFollowTheGenerationHalo() {
        assertEquals(11, LoopLoadingLimits.pinnedGenerationRadius());
        assertEquals(13, LoopLoadingLimits.loadedHaloRadius());
        assertEquals(16, LoopLoadingLimits.minimumRadius());
        // viewDistance + halo < radius
        assertEquals(2, LoopLoadingLimits.maxDistance(16));
        assertEquals(15, LoopLoadingLimits.maxDistance(29));
        assertEquals(18, LoopLoadingLimits.maxDistance(32));
        assertEquals(18, LoopLoadingLimits.defaultRadius() - LoopLoadingLimits.loadedHaloRadius() - 1);
    }

    @Test
    void disabledLoopDoesNotRestrictDistance() {
        assertDoesNotThrow(() -> LoopLoadingLimits.validateRadius(0));
        assertEquals(Integer.MAX_VALUE, LoopLoadingLimits.maxDistance(0));
    }

    @Test
    void legacySmallRadiusFailsWithAnExplicitDiagnostic() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> LoopLoadingLimits.validateRadius(3));
        assertTrue(exception.getMessage().contains("radius 3"));
        assertTrue(exception.getMessage().contains("minimum supported radius is " + LoopLoadingLimits.minimumRadius()));
        assertTrue(exception.getMessage().contains("saved period cannot be changed automatically"));
        assertThrows(IllegalArgumentException.class, () -> LoopLoadingLimits.maxDistance(3));
    }

    @Test
    void supportedBoundaryLeavesAtLeastTwoChunksOfDistance() {
        int minimum = LoopLoadingLimits.minimumRadius();
        assertThrows(IllegalArgumentException.class, () -> LoopLoadingLimits.validateRadius(minimum - 1));
        assertThrows(IllegalArgumentException.class, () -> LoopLoadingLimits.validateRadius(-1));
        assertDoesNotThrow(() -> LoopLoadingLimits.validateRadius(minimum));
        assertEquals(2, LoopLoadingLimits.maxDistance(minimum));
        assertEquals(3, LoopLoadingLimits.maxDistance(minimum + 1));
    }
}
