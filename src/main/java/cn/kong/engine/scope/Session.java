package cn.kong.engine.scope;

import java.util.concurrent.atomic.AtomicReference;

import cn.kong.engine.window.ContextWindow;

/**
 * 会话聚合根：窗口 + 待办 + 累计状态。
 *
 * <p>并发约定：状态迁移全部 CAS，同一会话同一时刻至多一个 RUNNING——
 * 并发正确性由内核保证，不依赖外部模块自觉。
 * 中断是"标记 + 状态迁移"：循环在检查点观察标志并优雅停止。
 */
public final class Session {

    private final String id;
    private final ContextWindow window;
    private final TodoBoard todos;
    private final SessionState state = new SessionState();
    private final AtomicReference<SessionStatus> status = new AtomicReference<>(SessionStatus.IDLE);
    private volatile boolean interruptRequested;

    public Session(String id, ContextWindow window, TodoBoard todos) {
        this.id = id;
        this.window = window;
        this.todos = todos;
    }

    // ---- 状态迁移（CAS） ----

    /** 抢占运行权：IDLE→RUNNING。 */
    public boolean tryAcquire() {
        return status.compareAndSet(SessionStatus.IDLE, SessionStatus.RUNNING);
    }

    public SessionStatus status() {
        return status.get();
    }

    /** run 结束（无论何种终止）统一回 IDLE，并清中断标志。 */
    public void release() {
        status.set(SessionStatus.IDLE);
        interruptRequested = false;
    }

    // ---- 中断 ----

    public void requestInterrupt() {
        if (status.compareAndSet(SessionStatus.RUNNING, SessionStatus.STOPPING)) {
            interruptRequested = true;
        }
    }

    public boolean isInterruptRequested() {
        return interruptRequested;
    }

    // ---- 视图 ----

    public String id() {
        return id;
    }

    public ContextWindow window() {
        return window;
    }

    public TodoBoard todos() {
        return todos;
    }

    public SessionState state() {
        return state;
    }
}
