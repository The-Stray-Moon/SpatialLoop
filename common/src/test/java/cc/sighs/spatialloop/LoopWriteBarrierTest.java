package cc.sighs.spatialloop;

import static org.junit.jupiter.api.Assertions.*;

import cc.sighs.spatialloop.ownership.LoopChunkOwnership;
import cc.sighs.spatialloop.ownership.LoopWriteBarrier;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class LoopWriteBarrierTest {
    @Test
    void waitsForEverySubmittedWriteNotJustTheLastOne() {
        LoopWriteBarrier writes = new LoopWriteBarrier();
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        assertSame(first, writes.submit(7, () -> first));
        writes.submit(7, () -> second);
        CompletableFuture<Void> saved = writes.snapshot(7);
        second.complete(null);
        assertFalse(saved.isDone());
        assertTrue(writes.snapshot(8).isDone());
        first.complete(null);
        saved.join();
        writes.snapshot(7).join();
    }

    @Test
    void failureCannotBeHiddenByLoggingHandlerOrLaterSuccess() {
        LoopWriteBarrier writes = new LoopWriteBarrier();
        CompletableFuture<Void> disk = new CompletableFuture<>();
        CompletableFuture<Void> logged = writes.submit(1, () -> disk).exceptionally(failure -> null);
        disk.completeExceptionally(new IOException("disk full"));
        logged.join();
        writes.submit(1, () -> CompletableFuture.completedFuture(null));
        CompletionException failure = assertThrows(CompletionException.class, () -> writes.snapshot(1).join());
        assertInstanceOf(IOException.class, failure.getCause());
    }

    @Test
    void synchronousSerializationFailureIsAlsoRetained() {
        LoopWriteBarrier writes = new LoopWriteBarrier();
        CompletableFuture<Void> result = writes.submit(1, () -> { throw new IllegalStateException("serialize"); });
        assertThrows(CompletionException.class, result::join);
        assertThrows(CompletionException.class, () -> writes.snapshot(1).join());
    }

    @Test
    void callerCannotCompleteOrCancelTheInternalBarrier() {
        LoopWriteBarrier writes = new LoopWriteBarrier();
        CompletableFuture<Void> disk = new CompletableFuture<>();
        writes.submit(1, () -> disk);
        writes.snapshot(1).complete(null);
        writes.snapshot(1).cancel(false);
        assertFalse(writes.snapshot(1).isDone());
        disk.complete(null);
        writes.snapshot(1).join();
    }

    @Test
    void earlierSuccessfulCompletionDoesNotForgetNewerWrite() {
        LoopWriteBarrier writes = new LoopWriteBarrier();
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> second = new CompletableFuture<>();
        writes.submit(1, () -> first);
        CompletableFuture<Void> beforeSecond = writes.snapshot(1);
        writes.submit(1, () -> second);
        first.complete(null);
        beforeSecond.join();
        assertFalse(writes.snapshot(1).isDone());
        second.complete(null);
        writes.snapshot(1).join();
    }

    @Test
    void threeStoresMustFinishBeforeNewOwnerCanRead() {
        LoopChunkOwnership ownership = new LoopChunkOwnership();
        LoopChunkOwnership.Claim old = ownership.claimRange(List.of(new LoopChunkOwnership.Request(1, 1))).orElseThrow();
        LoopChunkOwnership.Token token = old.tokens().get(0);
        ownership.releaseDemand(old);
        ownership.beginRetirement(token);
        LoopWriteBarrier chunks = new LoopWriteBarrier();
        LoopWriteBarrier entities = new LoopWriteBarrier();
        LoopWriteBarrier poi = new LoopWriteBarrier();
        CompletableFuture<Void> chunkWrite = new CompletableFuture<>();
        CompletableFuture<Void> entityWrite = new CompletableFuture<>();
        CompletableFuture<Void> poiWrite = new CompletableFuture<>();
        chunks.submit(1, () -> chunkWrite);
        entities.submit(1, () -> entityWrite);
        poi.submit(1, () -> poiWrite);
        ownership.flush(token, CompletableFuture.allOf(chunks.snapshot(1), entities.snapshot(1), poi.snapshot(1)));
        chunkWrite.complete(null);
        entityWrite.complete(null);
        assertTrue(ownership.claimRange(List.of(new LoopChunkOwnership.Request(1, 101))).isEmpty());
        poiWrite.complete(null);
        assertTrue(ownership.claimRange(List.of(new LoopChunkOwnership.Request(1, 101))).isPresent());
    }

    @Test
    void failedStorageKeepsOwnerClosedAfterOtherStoresFinish() {
        LoopChunkOwnership ownership = new LoopChunkOwnership();
        LoopChunkOwnership.Claim old = ownership.claimRange(List.of(new LoopChunkOwnership.Request(1, 1))).orElseThrow();
        LoopChunkOwnership.Token token = old.tokens().get(0);
        ownership.releaseDemand(old);
        ownership.beginRetirement(token);
        LoopWriteBarrier writes = new LoopWriteBarrier();
        CompletableFuture<Void> first = new CompletableFuture<>();
        CompletableFuture<Void> last = new CompletableFuture<>();
        writes.submit(1, () -> first);
        writes.submit(1, () -> last);
        ownership.flush(token, writes.snapshot(1));
        first.completeExceptionally(new IOException("failed"));
        assertEquals(LoopChunkOwnership.State.FLUSHING, ownership.snapshot(1).orElseThrow().state());
        last.complete(null);
        assertEquals(LoopChunkOwnership.State.FAILED, ownership.snapshot(1).orElseThrow().state());
        assertTrue(ownership.claimRange(List.of(new LoopChunkOwnership.Request(1, 101))).isEmpty());
    }
}
