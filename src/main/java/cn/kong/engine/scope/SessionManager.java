package cn.kong.engine.scope;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import cn.kong.engine.event.EventPublisher;
import cn.kong.engine.port.store.LedgerPort;
import cn.kong.engine.port.store.SnapshotPort;
import cn.kong.engine.port.store.SnapshotPort.SessionSnapshot;
import cn.kong.engine.port.context.TokenEstimator;
import cn.kong.engine.window.ContextWindow;

/**
 * 会话管理器：缓存会话、从账本+快照重建、并发互斥的第一道闸。
 * 恢复 = 账本重放重建窗口 + 快照恢复待办与累计状态。
 *
 * <p>淘汰策略（TTL 清理）P1 补充；P0 会话常驻缓存。
 */
public final class SessionManager {

    private final ConcurrentHashMap<String, Session> cache = new ConcurrentHashMap<>();
    private final LedgerPort ledger;
    private final SnapshotPort snapshots;
    private final EventPublisher events;
    private final TokenEstimator estimator;

    public SessionManager(LedgerPort ledger, SnapshotPort snapshots, EventPublisher events, TokenEstimator estimator) {
        this.ledger = ledger;
        this.snapshots = snapshots;
        this.events = events;
        this.estimator = estimator;
    }

    /** 取会话：缓存命中或从持久层重建。返回的会话可能忙（调用方需 tryAcquire）。 */
    public Session acquire(String sessionId) {
        return cache.computeIfAbsent(sessionId, this::load);
    }

    public Session peek(String sessionId) {
        return cache.get(sessionId);
    }

    private Session load(String id) {
        Session session = new Session(id, new ContextWindow(estimator), new TodoBoard(id, events));
        ledger.replay(id, 1).forEach(r -> session.window().addFromMessage(r.msg()));
        Optional<SessionSnapshot> snap = snapshots.load(id);
        snap.ifPresent(s -> {
            session.state().restore(s);
            session.todos().restore(s.todos());
        });
        return session;
    }
}
