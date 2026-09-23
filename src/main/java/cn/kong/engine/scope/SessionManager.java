package cn.kong.engine.scope;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import cn.kong.engine.event.EventPublisher;
import cn.kong.engine.port.store.LedgerPort;
import cn.kong.engine.port.store.SnapshotPort;
import cn.kong.engine.port.store.SnapshotPort.SessionSnapshot;
import cn.kong.engine.port.context.TokenEstimator;
import cn.kong.engine.window.ContextWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 会话管理器：缓存会话、从账本+快照重建、并发互斥的第一道闸。
 * 恢复 = 账本重放重建窗口 + 快照恢复待办与累计状态。
 *
 * <p>淘汰（{@link #evict}）：外部删除会话或回收内存时调用，仅清缓存不动磁盘；
 * 再次 acquire 将从持久层重建。运行中的会话拒绝淘汰。
 */
public final class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

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

    /**
     * 淘汰会话缓存：空闲则移除并返回 true；缓存中不存在视为成功（幂等）；
     * 运行中（RUNNING/STOPPING）返回 false。
     *
     * <p>仅清内存，不触碰持久层——磁盘投影的清理由外部（登记属主）负责。
     */
    public boolean evict(String sessionId) {
        Session session = cache.get(sessionId);
        if (session == null) {
            return true;
        }
        if (!session.evictIfIdle()) {
            return false;
        }
        // 条件移除：只移除刚淘汰的实例，避免与并发 acquire 装载的新实例冲突
        cache.remove(sessionId, session);
        return true;
    }

    private Session load(String id) {
        Session session = new Session(id, new ContextWindow(estimator), new TodoBoard(id, events));
        ledger.replay(id, 1).forEach(r -> session.window().addFromMessage(r.msg()));
        Optional<SessionSnapshot> snap = snapshots.load(id);
        snap.ifPresent(s -> {
            session.state().restore(s);
            session.todos().restore(s.todos());
        });
        log.info("[Session] 从持久层重建 session={}, blocks={}, 有快照={}", id, session.window().size(), snap.isPresent());
        return session;
    }
}
