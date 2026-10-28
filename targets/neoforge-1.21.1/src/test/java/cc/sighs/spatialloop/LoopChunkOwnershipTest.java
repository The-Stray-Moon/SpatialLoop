package cc.sighs.spatialloop;

import cc.sighs.spatialloop.ownership.LoopChunkOwnership;
import cc.sighs.spatialloop.ownership.LoopChunkOwnership.Claim;
import cc.sighs.spatialloop.ownership.LoopChunkOwnership.Request;
import cc.sighs.spatialloop.ownership.LoopChunkOwnership.State;
import cc.sighs.spatialloop.ownership.LoopChunkOwnership.Token;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 范围准入与保存屏障的状态机测试，不代表 Minecraft 已接入所有权交接。 */
class LoopChunkOwnershipTest {
    private final LoopChunkOwnership ownership = new LoopChunkOwnership();

    @Test
    void conflictingRangeChangesNeitherExistingDemandsNorFreeSlots() {
        Claim a = claim(1, 101);
        Claim b = claim(2, 102);
        var beforeA = ownership.snapshot(1);
        var beforeB = ownership.snapshot(2);
        assertTrue(ownership.claimRange(List.of(
                new Request(1, 101), new Request(3, 103), new Request(2, 202))).isEmpty());
        assertEquals(beforeA, ownership.snapshot(1));
        assertEquals(beforeB, ownership.snapshot(2));
        assertTrue(ownership.snapshot(3).isEmpty());
        assertEquals(b.tokens().getFirst().generation() + 1, claim(3, 103).tokens().getFirst().generation());
        assertFalse(ownership.beginRetirement(a.tokens().getFirst()));
    }

    @Test
    void selfConflictDoesNotClaimAnyPartOfRange() {
        assertTrue(ownership.claimRange(List.of(
                new Request(1, 101), new Request(2, 102), new Request(1, 201))).isEmpty());
        assertTrue(ownership.snapshot(1).isEmpty());
        assertTrue(ownership.snapshot(2).isEmpty());
        assertEquals(1, claim(1, 101).tokens().getFirst().generation());
    }

    @Test
    void sameAbsoluteSharesOwnerButEveryEffectiveDemandMustLeave() {
        Claim a = ownership.claimRange(List.of(new Request(1, 101), new Request(1, 101))).orElseThrow();
        Claim b = claim(1, 101);
        Token token = a.tokens().getFirst();
        assertEquals(1, a.tokens().size());
        assertSame(token, b.tokens().getFirst());
        assertEquals(2, ownership.snapshot(1).orElseThrow().demands());
        assertFalse(ownership.beginRetirement(token));
        assertTrue(ownership.releaseDemand(a));
        assertFalse(ownership.releaseDemand(a));
        assertFalse(ownership.beginRetirement(token));
        assertTrue(ownership.claimRange(List.of(new Request(1, 201))).isEmpty());
        assertTrue(ownership.releaseDemand(b));
        assertTrue(ownership.beginRetirement(token));
    }

    @Test
    void idleOwnerStillRequiresRetirementAndRetirementRejectsReentry() {
        Claim a = claim(1, 101);
        Token token = a.tokens().getFirst();
        ownership.releaseDemand(a);
        assertTrue(ownership.claimRange(List.of(new Request(1, 201))).isEmpty());
        assertTrue(ownership.beginRetirement(token));
        assertEquals(State.RETIRING, ownership.snapshot(1).orElseThrow().state());
        assertFalse(ownership.beginRetirement(token));
        assertTrue(ownership.claimRange(List.of(new Request(1, 101))).isEmpty());
        assertTrue(ownership.claimRange(List.of(new Request(1, 201))).isEmpty());
    }

    @Test
    void delayedSaveKeepsOwnerUntilEveryStoreCompletes() {
        Token token = retire(1, 101);
        CompletableFuture<Void> chunks = new CompletableFuture<>();
        CompletableFuture<Void> entities = new CompletableFuture<>();
        CompletableFuture<Void> poi = new CompletableFuture<>();
        assertTrue(ownership.flush(token, CompletableFuture.allOf(chunks, entities, poi)));
        assertEquals(State.FLUSHING, ownership.snapshot(1).orElseThrow().state());
        assertFalse(ownership.flush(token, CompletableFuture.completedFuture(null)));
        chunks.complete(null);
        entities.complete(null);
        assertTrue(ownership.claimRange(List.of(new Request(1, 101))).isEmpty());
        assertTrue(ownership.claimRange(List.of(new Request(1, 201))).isEmpty());
        poi.complete(null);
        assertTrue(ownership.snapshot(1).isEmpty());
        assertEquals(201, claim(1, 201).tokens().getFirst().absolute());
    }

    @Test
    void failedSaveKeepsOwnerAndCannotBeReplacedBySuccessfulFuture() {
        Token token = retire(1, 101);
        CompletableFuture<Void> saved = new CompletableFuture<>();
        ownership.flush(token, saved);
        RuntimeException failure = new RuntimeException("disk full");
        saved.completeExceptionally(failure);
        var snapshot = ownership.snapshot(1).orElseThrow();
        assertEquals(State.FAILED, snapshot.state());
        assertSame(failure, snapshot.failure());
        assertSame(token, snapshot.token());
        assertFalse(ownership.flush(token, CompletableFuture.completedFuture(null)));
        assertFalse(ownership.beginRetirement(token));
        assertTrue(ownership.claimRange(List.of(new Request(1, 101))).isEmpty());
        assertTrue(ownership.claimRange(List.of(new Request(1, 201))).isEmpty());
    }

    @Test
    void cancelledSaveAlsoFailsClosed() {
        Token token = retire(1, 101);
        CompletableFuture<Void> saved = new CompletableFuture<>();
        ownership.flush(token, saved);
        saved.cancel(false);
        assertEquals(State.FAILED, ownership.snapshot(1).orElseThrow().state());
    }

    @Test
    void aToBToARejectsStaleTokensAndLateCallbacks() {
        Token oldA = retire(1, 101);
        // 可重复发出回调的测试屏障，模拟旧异步回调迟到/重复；正常 future 只会完成一次。
        RepeatedCompletion saved = new RepeatedCompletion();
        ownership.flush(oldA, saved);
        saved.callback.accept(null, null);
        Token b = retire(1, 201);
        ownership.flush(b, CompletableFuture.completedFuture(null));
        Token newA = retire(1, 101);
        CompletableFuture<Void> newSave = new CompletableFuture<>();
        ownership.flush(newA, newSave);
        assertNotEquals(oldA.generation(), newA.generation());
        assertFalse(ownership.beginRetirement(oldA));
        assertFalse(ownership.flush(oldA, CompletableFuture.completedFuture(null)));
        saved.callback.accept(null, new RuntimeException("late failure"));
        saved.callback.accept(null, null);
        assertSame(newA, ownership.snapshot(1).orElseThrow().token());
        assertEquals(State.FLUSHING, ownership.snapshot(1).orElseThrow().state());
        newSave.complete(null);
        assertTrue(ownership.snapshot(1).isEmpty());
    }

    @Test
    void foreignHandlesAndActiveFlushCannotChangeState() {
        Claim claim = claim(1, 101);
        LoopChunkOwnership other = new LoopChunkOwnership();
        assertFalse(other.releaseDemand(claim));
        assertFalse(other.beginRetirement(claim.tokens().getFirst()));
        assertFalse(ownership.flush(claim.tokens().getFirst(), CompletableFuture.completedFuture(null)));
        assertEquals(1, ownership.snapshot(1).orElseThrow().demands());
    }

    @Test
    void concurrentOverlappingRangesHaveExactlyOneWinner() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return ownership.claimRange(List.of(new Request(1, 101), new Request(2, 102)));
            });
            var b = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return ownership.claimRange(List.of(new Request(2, 202), new Request(3, 203)));
            });
            var resultA = a.get(5, TimeUnit.SECONDS);
            var resultB = b.get(5, TimeUnit.SECONDS);
            assertNotEquals(resultA.isPresent(), resultB.isPresent());
            assertEquals(resultA.isPresent(), ownership.snapshot(1).isPresent());
            assertEquals(resultB.isPresent(), ownership.snapshot(3).isPresent());
            assertEquals(1, ownership.snapshot(2).orElseThrow().demands());
        }
    }

    private Claim claim(long canonical, long absolute) {
        return ownership.claimRange(List.of(new Request(canonical, absolute))).orElseThrow();
    }

    private Token retire(long canonical, long absolute) {
        Claim claim = claim(canonical, absolute);
        Token token = claim.tokens().getFirst();
        assertTrue(ownership.releaseDemand(claim));
        assertTrue(ownership.beginRetirement(token));
        return token;
    }

    private static final class RepeatedCompletion extends CompletableFuture<Void> {
        private java.util.function.BiConsumer<? super Void, ? super Throwable> callback;

        @Override
        public CompletableFuture<Void> whenComplete(
                java.util.function.BiConsumer<? super Void, ? super Throwable> callback) {
            this.callback = callback;
            return this;
        }
    }
}
