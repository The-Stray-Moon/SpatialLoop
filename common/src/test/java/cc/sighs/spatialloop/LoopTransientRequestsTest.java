package cc.sighs.spatialloop;

import cc.sighs.spatialloop.ownership.LoopTransientRequests;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoopTransientRequestsTest {
    private final LoopTransientRequests requests = new LoopTransientRequests();

    @Test
    void completedRequestIsEligible() {
        requests.begin(1);
        requests.finish(1);
        assertTrue(requests.canRetire(1));
    }

    @Test
    void activeWaitProtectsPreviouslyCompletedSource() {
        requests.begin(1);
        requests.finish(1);
        requests.begin(1);
        assertFalse(requests.canRetire(1));
        requests.finish(1);
        assertTrue(requests.canRetire(1));
    }

    @Test
    void nestedRequestsMustAllComplete() {
        requests.begin(1);
        requests.begin(1);
        requests.finish(1);
        assertFalse(requests.canRetire(1));
        requests.finish(1);
        assertTrue(requests.canRetire(1));
    }

    @Test
    void freshUnknownSourceIsNeverEligible() {
        assertFalse(requests.canRetire(1));
        requests.begin(2);
        requests.finish(2);
        assertFalse(requests.canRetire(1));
    }

    @Test
    void unbalancedFinishCannotMakeSourceEligible() {
        assertThrows(IllegalStateException.class, () -> requests.finish(1));
        assertFalse(requests.canRetire(1));
        requests.begin(1);
        requests.finish(1);
        assertThrows(IllegalStateException.class, () -> requests.finish(1));
    }

    @Test
    void workerCompletionsPreserveOtherActiveDemand() {
        requests.begin(1);
        requests.begin(1);
        CompletableFuture.runAsync(() -> requests.finish(1)).join();
        assertFalse(requests.canRetire(1));
        CompletableFuture.runAsync(() -> requests.finish(1)).join();
        assertTrue(requests.canRetire(1));
    }
}
