package cc.sighs.spatialloop.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LoopGenerationReachTest {
    @Test
    void requestedReachDefinesTheAtomicSquareSize() {
        assertEquals(1, LoopGenerationReach.squareDependencyCount(0));
        assertEquals(25, LoopGenerationReach.squareDependencyCount(2));
        assertEquals(529, LoopGenerationReach.squareDependencyCount(11));
        assertTrue(LoopGenerationReach.squareDependencyCount(2)
                < LoopGenerationReach.squareDependencyCount(11));
        assertThrows(IllegalArgumentException.class, () -> LoopGenerationReach.squareDependencyCount(-1));
    }
}
