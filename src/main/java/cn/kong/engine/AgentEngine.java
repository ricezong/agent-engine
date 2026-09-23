package cn.kong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import cn.kong.engine.event.EngineEvent;
import cn.kong.engine.event.EventPublisher;
import cn.kong.engine.hook.Hook;
import cn.kong.engine.hook.HookChain;
import cn.kong.engine.loop.RunResult;
import cn.kong.engine.loop.TurnLoop;
import cn.kong.engine.port.store.ArtifactPort;
import cn.kong.engine.port.context.ContextAssembler;
import cn.kong.engine.port.context.EnvironmentProbe;
import cn.kong.engine.event.EventSink;
import cn.kong.engine.port.interaction.InteractionChannel;
import cn.kong.engine.port.store.LedgerPort;
import cn.kong.engine.port.model.LlmPort;
import cn.kong.engine.port.store.MemoryPort;
import cn.kong.engine.port.store.SnapshotPort;
import cn.kong.engine.port.context.TokenEstimator;
import cn.kong.engine.port.tool.ToolSet;
import cn.kong.engine.port.store.Workspace;
import cn.kong.engine.scope.RunHandle;
import cn.kong.engine.scope.Session;
import cn.kong.engine.scope.SessionManager;
import cn.kong.engine.scope.SessionStatus;
import cn.kong.engine.stop.StopCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 引擎门面——唯一的对外入口。
 *
 * <p>命令：run / interrupt / answer / evict / close（会改变引擎状态）
 * 事件：subscribe（只读流，传输层从这里接线）
 *
 * <p>装配：Builder 显式注入全部端口，build() 校验完整性。引擎不自带任何端口实现
 */
public final class AgentEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgentEngine.class);

    private final EngineConfig config;
    private final EventPublisher events = new EventPublisher();
    private final SessionManager sessions;
    private final TurnLoop loop;
    private final InteractionChannel interaction;
    private final ExecutorService runPool;
    private final ExecutorService toolPool;

    private AgentEngine(Builder b) {
        this.config = b.config;
        this.interaction = b.interaction;

        this.runPool = Executors.newCachedThreadPool(r -> named(r, "engine-run"));
        this.toolPool = Executors.newFixedThreadPool(
                Math.max(1, config.toolParallelism()), r -> named(r, "engine-tool"));

        this.sessions = new SessionManager(b.ledger, b.snapshots, events, b.estimator);
        HookChain hooks = new HookChain(b.hooks, events);
        TurnLoop.Deps deps = new TurnLoop.Deps(
                b.llm, b.tools, hooks, b.assembler, b.estimator, b.memory,
                b.environment, b.ledger, b.snapshots, b.artifacts,
                b.interaction, b.workspace, events);
        this.loop = new TurnLoop(config, deps, toolPool);
    }

    private static Thread named(Runnable r, String prefix) {
        Thread t = new Thread(r, prefix + "-" + SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    }

    private static final AtomicLong SEQ = new AtomicLong();

    // ---- 命令 ----

    /**
     * 发起一次运行。同一会话同一时刻至多一个 RUNNING，忙时返回 REJECTED_BUSY。
     */
    public RunHandle run(String sessionId, String userInput) {
        Session session = sessions.acquire(sessionId);
        boolean resumed = session.window().size() > 0;
        if (!session.tryAcquire()) {
            log.warn("[Engine] 拒绝并发运行 session={}", sessionId);
            return RunHandle.completed(new RunResult(
                    StopCategory.REJECTED_BUSY, "会话正在运行中", session.state().totalUsage()));
        }
        events.publish(new EngineEvent.RunStarted(sessionId, resumed));
        events.publish(new EngineEvent.SessionStatusChanged(sessionId, SessionStatus.RUNNING));
        Future<RunResult> future = runPool.submit(() -> {
            try {
                return loop.run(session, userInput);
            } finally {
                session.release();
                events.publish(new EngineEvent.SessionStatusChanged(sessionId, SessionStatus.IDLE));
            }
        });
        return new RunHandle(future);
    }

    /** 中断运行（标记式软中断：当前检查点后优雅停止）。 */
    public boolean interrupt(String sessionId) {
        Session session = sessions.peek(sessionId);
        if (session == null) {
            return false;
        }
        log.info("[Engine] 请求中断 session={}, 当前状态={}", sessionId, session.status());
        session.requestInterrupt();
        return true;
    }

    /** 回答引擎提问（异步交互通道用；同步通道返回 false）。 */
    public boolean answer(String sessionId, String questionId, List<InteractionChannel.Answer> answers) {
        return interaction.deliverAnswer(questionId, answers);
    }

    /**
     * 淘汰会话缓存（内存回收/删除前置）。
     */
    public boolean evict(String sessionId) {
        boolean evicted = sessions.evict(sessionId);
        if (evicted) {
            log.info("[Engine] 会话缓存已淘汰: {}", sessionId);
        }
        return evicted;
    }

    /** 订阅事件流（只读）。 */
    public void subscribe(EventSink sink) {
        events.subscribe(sink);
    }

    /** 退订事件流 */
    public void unsubscribe(EventSink sink) {
        events.unsubscribe(sink);
    }

    /**
     * 优雅关闭：拒绝新 run，等待在途 run 结束（上限 = 工具超时 + 30 秒）。
     * 每个 run 的终态已由 RunFinished 事件覆盖（事件模型是会话级的，不设引擎级终态事件）。
     */
    public void shutdown() {
        runPool.shutdown();
        try {
            if (!runPool.awaitTermination(
                    config.toolTimeout().plusSeconds(30).toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("[Engine] 优雅关闭超时，仍有在途 run");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        toolPool.shutdown();
    }

    /** 立即关闭：中断在途线程（在途 run 以 INTERNAL_ERROR 收尾并照常发 RunFinished）。 */
    @Override
    public void close() {
        runPool.shutdownNow();
        toolPool.shutdownNow();
    }

    // ---- 装配 ----

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 显式装配：所有端口在这里注入，依赖关系一眼可见。
     */
    public static final class Builder {

        private EngineConfig config = EngineConfig.defaults();
        private final List<Hook> hooks = new ArrayList<>();
        private LlmPort llm;
        private ToolSet tools;
        private ContextAssembler assembler;
        private TokenEstimator estimator;
        private MemoryPort memory;
        private EnvironmentProbe environment;
        private LedgerPort ledger;
        private SnapshotPort snapshots;
        private ArtifactPort artifacts;
        private InteractionChannel interaction;
        private Workspace workspace;

        public Builder config(EngineConfig v) {
            this.config = v;
            return this;
        }

        public Builder llm(LlmPort v) {
            this.llm = v;
            return this;
        }

        public Builder tools(ToolSet v) {
            this.tools = v;
            return this;
        }

        public Builder hooks(Hook... values) {
            for (Hook h : values) {
                hooks.add(h);
            }
            return this;
        }

        public Builder assembler(ContextAssembler v) {
            this.assembler = v;
            return this;
        }

        public Builder estimator(TokenEstimator v) {
            this.estimator = v;
            return this;
        }

        public Builder memory(MemoryPort v) {
            this.memory = v;
            return this;
        }

        public Builder environment(EnvironmentProbe v) {
            this.environment = v;
            return this;
        }

        public Builder ledger(LedgerPort v) {
            this.ledger = v;
            return this;
        }

        public Builder snapshots(SnapshotPort v) {
            this.snapshots = v;
            return this;
        }

        public Builder artifacts(ArtifactPort v) {
            this.artifacts = v;
            return this;
        }

        public Builder interaction(InteractionChannel v) {
            this.interaction = v;
            return this;
        }

        public Builder workspace(Workspace v) {
            this.workspace = v;
            return this;
        }

        public AgentEngine build() {
            List<String> missing = new ArrayList<>();
            if (llm == null) missing.add("llm");
            if (tools == null) missing.add("tools");
            if (assembler == null) missing.add("assembler");
            if (estimator == null) missing.add("estimator");
            if (memory == null) missing.add("memory");
            if (environment == null) missing.add("environment");
            if (ledger == null) missing.add("ledger");
            if (snapshots == null) missing.add("snapshots");
            if (artifacts == null) missing.add("artifacts");
            if (interaction == null) missing.add("interaction");
            if (workspace == null) missing.add("workspace");
            if (!missing.isEmpty()) {
                throw new EngineException("以下端口未注入: " + String.join(", ", missing));
            }
            return new AgentEngine(this);
        }
    }
}
