package cc.sighs.spatialloop.world;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoopLoadingLimitsTest {

    @Test
    void limitsReserveThePinnedVersionGenerationDependencyRadius() {
        assertEquals(16, LoopLoadingLimits.minimumRadius());
        assertEquals(18, LoopLoadingLimits.maxDistance(32));
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
