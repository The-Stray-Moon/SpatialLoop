package cc.sighs.spatialloop.ownership;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 单维度的纯 Java 所有权协调器；所有状态转换在同一把锁下完成。
 *
 * <p>调用方负责把整个生成依赖范围转换为 canonical/absolute 键对，并把所有有效需求
 * （票据、预读、生成依赖等）表示为 Claim。这里不计算坐标，也不操作 Minecraft 票据或存储。
 * 退役前必须释放全部需求；退役后调用方必须停止该 owner 的生产者、等待卸载与所有写入提交，
 * 再提供覆盖区块、实体、POI 等全部保存的屏障。不能用丢弃旧写入或仅完成入队的 future 代替保存完成。
 * FAILED 保留所有权，没有强制释放接口，避免保存失败后加载陈旧副本。
 */
public final class LoopChunkOwnership {
    public enum State { ACTIVE, RETIRING, FLUSHING, FAILED }

    public record Request(long canonical, long absolute) { }

    /** 不可伪造的 generation token；即使 absolute 经 A-B-A 回到原值也不会重用。 */
    public static final class Token {
        private final long canonical;
        private final long absolute;
        private final long generation;

        private Token(long canonical, long absolute, long generation) {
            this.canonical = canonical;
            this.absolute = absolute;
            this.generation = generation;
        }

        public long canonical() { return canonical; }
        public long absolute() { return absolute; }
        public long generation() { return generation; }
    }

    /** 一次范围申请的一份有效需求，必须整体释放且只能释放一次。 */
    public static final class Claim {
        private final LoopChunkOwnership coordinator;
        private final List<Token> tokens;
        private boolean released;

        private Claim(LoopChunkOwnership coordinator, List<Token> tokens) {
            this.coordinator = coordinator;
            this.tokens = List.copyOf(tokens);
        }

        public List<Token> tokens() { return tokens; }
    }

    public record Snapshot(Token token, State state, int demands, Throwable failure) { }

    private static final class Owner {
        private final Token token;
        private State state = State.ACTIVE;
        private int demands;
        private Throwable failure;

        private Owner(Token token) {
            this.token = token;
        }
    }

    private final Map<Long, Owner> owners = new LinkedHashMap<>();
    private long generation;

    /**
     * 全范围原子申请。重复的同一键对仅计一次需求；同余自冲突、不同 absolute owner、
     * 或非 ACTIVE owner 均返回 empty，且不修改任何状态（包括 generation 与需求计数）。
     */
    public synchronized Optional<Claim> claimRange(Collection<Request> requests) {
        Objects.requireNonNull(requests, "requests");
        Map<Long, Long> range = new LinkedHashMap<>();
        for (Request request : requests) {
            Objects.requireNonNull(request, "request");
            Long previous = range.putIfAbsent(request.canonical(), request.absolute());
            if (previous != null && previous.longValue() != request.absolute()) {
                return Optional.empty();
            }
        }
        long newOwners = 0;
        for (Map.Entry<Long, Long> request : range.entrySet()) {
            Owner owner = owners.get(request.getKey());
            if (owner == null) {
                newOwners++;
            } else if (owner.state != State.ACTIVE || owner.token.absolute != request.getValue()
                    || owner.demands == Integer.MAX_VALUE) {
                return Optional.empty();
            }
        }
        if (newOwners > Long.MAX_VALUE - generation) {
            throw new IllegalStateException("Ownership generation exhausted");
        }
        List<Token> tokens = new java.util.ArrayList<>();
        for (Map.Entry<Long, Long> request : range.entrySet()) {
            Owner owner = owners.get(request.getKey());
            if (owner == null) {
                owner = new Owner(new Token(request.getKey(), request.getValue(), ++generation));
                owners.put(request.getKey(), owner);
            }
            owner.demands++;
            tokens.add(owner.token);
        }
        return Optional.of(new Claim(this, tokens));
    }

    /** 仅撤销需求，不释放所有权。重复释放或来自另一协调器的句柄返回 false。 */
    public synchronized boolean releaseDemand(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        if (claim.coordinator != this || claim.released) {
            return false;
        }
        for (Token token : claim.tokens) {
            owners.get(token.canonical).demands--;
        }
        claim.released = true;
        return true;
    }

    public synchronized boolean beginRetirement(Token token) {
        Owner owner = current(token);
        if (owner == null || owner.state != State.ACTIVE || owner.demands != 0) {
            return false;
        }
        owner.state = State.RETIRING;
        return true;
    }

    /**
     * 绑定真实保存完成屏障。成功回调才释放；异常或取消转 FAILED，保留原 owner。
     * token 过期或已绑定屏障时返回 false。完成回调也检查 generation token。
     */
    public synchronized boolean flush(Token token, CompletionStage<?> saved) {
        Objects.requireNonNull(saved, "saved");
        Owner owner = current(token);
        if (owner == null || owner.state != State.RETIRING) {
            return false;
        }
        owner.state = State.FLUSHING;
        try {
            saved.whenComplete((ignored, failure) -> finishFlush(token, failure));
        } catch (RuntimeException failure) {
            finishFlush(token, failure);
        }
        return true;
    }

    public synchronized Optional<Snapshot> snapshot(long canonical) {
        Owner owner = owners.get(canonical);
        return owner == null ? Optional.empty()
                : Optional.of(new Snapshot(owner.token, owner.state, owner.demands, owner.failure));
    }

    private synchronized void finishFlush(Token token, Throwable failure) {
        Owner owner = current(token);
        if (owner == null || owner.state != State.FLUSHING) {
            return;
        }
        if (failure == null) {
            owners.remove(token.canonical);
        } else {
            owner.failure = failure;
            owner.state = State.FAILED;
        }
    }

    private Owner current(Token token) {
        Objects.requireNonNull(token, "token");
        Owner owner = owners.get(token.canonical);
        return owner != null && owner.token == token ? owner : null;
    }
}
