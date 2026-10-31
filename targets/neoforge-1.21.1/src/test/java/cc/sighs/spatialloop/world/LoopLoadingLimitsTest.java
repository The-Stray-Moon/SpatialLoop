package cc.sighs.spatialloop.world;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cc.sighs.spatialloop.spi.LoopGenerationProfile;
import org.junit.jupiter.api.Test;

/**
 * 视距上限不是拍脑袋的余量：{@code ChunkLevel.MAX_LEVEL} 比玩家加载票据等级
 * （ENTITY_TICKING = 31）多保留 13 格 holder；两倍持有半径必须小于一个周期。
 *
 * <p>{@code ChunkStatus}/{@code ChunkLevel} 的静态初始化需要注册表 bootstrap，单元测试里读不到，
 * 所以「13 = MAX_LEVEL - 31」与「生成依赖半径 = 11」由 {@code ChunkMapOwnershipMixin} 在真实
 * 服务端构造维度时用 {@code LoopGenerationReach.forStatus(FULL)} 现场核对；这里用显式的
 * {@link LoopGenerationProfile} 钉住公式结果，不依赖 SPI 是否已安装。
 */
class LoopLoadingLimitsTest {
    private static final LoopGenerationProfile PROFILE = new LoopGenerationProfile() {
        @Override
        public int generationRadius() {
            return 11;
        }

        @Override
        public int loadedHaloRadius() {
            return 13;
        }
    };

    @Test
    void limitsFollowTheGenerationHalo() {
        assertEquals(11, LoopLoadingLimits.pinnedGenerationRadius(PROFILE));
        assertEquals(13, LoopLoadingLimits.loadedHaloRadius(PROFILE));
        assertEquals(16, LoopLoadingLimits.minimumRadius(PROFILE));
        // viewDistance + halo < radius
        assertEquals(2, LoopLoadingLimits.maxDistance(16, PROFILE));
        assertEquals(15, LoopLoadingLimits.maxDistance(29, PROFILE));
        assertEquals(18, LoopLoadingLimits.maxDistance(32, PROFILE));
        assertEquals(18, LoopLoadingLimits.defaultRadius() - LoopLoadingLimits.loadedHaloRadius(PROFILE) - 1);
    }

    @Test
    void disabledLoopDoesNotRestrictDistance() {
        assertDoesNotThrow(() -> LoopLoadingLimits.validateRadius(0, PROFILE));
        assertEquals(Integer.MAX_VALUE, LoopLoadingLimits.maxDistance(0, PROFILE));
    }

    @Test
    void legacySmallRadiusFailsWithAnExplicitDiagnostic() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> LoopLoadingLimits.validateRadius(3, PROFILE));
        assertTrue(exception.getMessage().contains("radius 3"));
        assertTrue(exception.getMessage().contains(
                "minimum supported radius is " + LoopLoadingLimits.minimumRadius(PROFILE)));
        assertTrue(exception.getMessage().contains("saved period cannot be changed automatically"));
        assertThrows(IllegalArgumentException.class, () -> LoopLoadingLimits.maxDistance(3, PROFILE));
    }

    @Test
    void supportedBoundaryLeavesAtLeastTwoChunksOfDistance() {
        int minimum = LoopLoadingLimits.minimumRadius(PROFILE);
        assertThrows(IllegalArgumentException.class, () -> LoopLoadingLimits.validateRadius(minimum - 1, PROFILE));
        assertThrows(IllegalArgumentException.class, () -> LoopLoadingLimits.validateRadius(-1, PROFILE));
        assertDoesNotThrow(() -> LoopLoadingLimits.validateRadius(minimum, PROFILE));
        assertEquals(2, LoopLoadingLimits.maxDistance(minimum, PROFILE));
        assertEquals(3, LoopLoadingLimits.maxDistance(minimum + 1, PROFILE));
    }
}
