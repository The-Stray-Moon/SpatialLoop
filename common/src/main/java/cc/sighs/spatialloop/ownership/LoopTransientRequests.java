package cc.sighs.spatialloop.ownership;

import java.util.HashMap;
import java.util.Map;

/** Tracks synchronous chunk requests, including completions on generation workers. */
public final class LoopTransientRequests {
    private final Map<Long, Integer> activeRequests = new HashMap<>();

    public synchronized void begin(long source) {
        activeRequests.put(source, Math.addExact(activeRequests.getOrDefault(source, 0), 1));
    }

    public synchronized void finish(long source) {
        Integer active = activeRequests.get(source);
        if (active == null || active == 0) {
            throw new IllegalStateException("No active chunk request at " + source);
        }
        // Keep zero: an unobserved source must never become eligible for retirement.
        activeRequests.put(source, active - 1);
    }

    public synchronized boolean canRetire(long source) {
        Integer active = activeRequests.get(source);
        return active != null && active == 0;
    }
}
