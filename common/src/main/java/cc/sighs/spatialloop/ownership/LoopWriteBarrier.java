package cc.sighs.spatialloop.ownership;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 单个存储实例按 canonical 槽位跟踪真实 IO 完成，而非保存入队。
 *
 * <p>屏障只是快照：调用方必须先停止旧 owner 的生产者并完成最终写入提交，再获取屏障。
 * 这里不阻止预读、不提供内存所有权，也不丢弃任何写入。失败永久保留在该存储实例内，
 * 后续成功不能掩盖早先失败。成功完成的槽位移除，避免跟随玩家移动无限增长。
 */
public final class LoopWriteBarrier {
    private final Map<Long, CompletableFuture<Void>> writes = new HashMap<>();

    public synchronized CompletableFuture<Void> submit(long canonical, Supplier<CompletableFuture<Void>> write) {
        Objects.requireNonNull(write, "write");
        CompletableFuture<Void> operation;
        try {
            operation = Objects.requireNonNull(write.get(), "write future");
        } catch (RuntimeException failure) {
            operation = CompletableFuture.failedFuture(failure);
        }
        CompletableFuture<Void> previous = writes.get(canonical);
        CompletableFuture<Void> combined = previous == null
                ? operation.copy() : CompletableFuture.allOf(previous, operation);
        writes.put(canonical, combined);
        combined.whenComplete((ignored, failure) -> {
            if (failure == null) {
                forgetSuccessful(canonical, combined);
            }
        });
        return operation;
    }

    /** 返回隔离的 future，调用方取消或完成它不会改变内部屏障。 */
    public synchronized CompletableFuture<Void> snapshot(long canonical) {
        CompletableFuture<Void> pending = writes.get(canonical);
        return pending == null ? CompletableFuture.completedFuture(null) : pending.copy();
    }

    private synchronized void forgetSuccessful(long canonical, CompletableFuture<Void> completed) {
        writes.remove(canonical, completed);
    }
}
