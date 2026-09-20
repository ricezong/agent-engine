package cn.kong.engine.loop;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import cn.kong.engine.EngineConfig;
import cn.kong.engine.event.EngineEvent;
import cn.kong.engine.event.EventPublisher;
import cn.kong.engine.hook.HookChain;
import cn.kong.engine.hook.HookChain.HookOutcome;
import cn.kong.engine.msg.ChatMsg;
import cn.kong.engine.msg.ToolCall;
import cn.kong.engine.msg.ToolResult;
import cn.kong.engine.port.store.ArtifactPort.ArtifactRef;
import cn.kong.engine.port.context.ContextAssembler;
import cn.kong.engine.port.context.ContextAssembler.AssemblyParts;
import cn.kong.engine.port.context.EnvironmentProbe;
import cn.kong.engine.port.interaction.InteractionChannel;
import cn.kong.engine.port.interaction.InteractionChannel.Decision;
import cn.kong.engine.port.model.LlmPort;
import cn.kong.engine.port.model.LlmPortException;
import cn.kong.engine.port.model.LlmReply;
import cn.kong.engine.port.model.LlmRequest;
import cn.kong.engine.port.store.MemoryPort;
import cn.kong.engine.port.store.LedgerPort;
import cn.kong.engine.port.store.SnapshotPort;
import cn.kong.engine.port.store.SnapshotPort.SessionSnapshot;
import cn.kong.engine.port.model.StreamCallbacks;
import cn.kong.engine.port.context.TokenEstimator;
import cn.kong.engine.port.store.Workspace;
import cn.kong.engine.port.store.ArtifactPort;
import cn.kong.engine.port.tool.ToolSet;
import cn.kong.engine.scope.Session;
import cn.kong.engine.scope.Task;
import cn.kong.engine.stop.StopCategory;
import cn.kong.engine.port.tool.ToolContext;

/**
 * 主循环——整个引擎唯一的 while。
 *
 * <p>每轮：PreModel 钩子 → 组装 → 模型 → PostModel 钩子 →（无工具调用则正常退出）
 * → PreTool 钩子（门禁）→ 并行执行工具 → PostTool 钩子 → 溢出关卡 → 回填账本与窗口。
 *
 * <p>中断检查点：轮首 / 组装前 / 工具执行前（标记式软中断，优雅停止）。
 */
public final class TurnLoop {

    private static final System.Logger LOG = System.getLogger(TurnLoop.class.getName());

    /** 依赖聚合：由 AgentEngine 装配后注入。 */
    public record Deps(
            LlmPort llm,
            ToolSet tools,
            HookChain hooks,
            ContextAssembler assembler,
            TokenEstimator estimator,
            MemoryPort memory,
            EnvironmentProbe environment,
            LedgerPort ledger,
            SnapshotPort snapshots,
            ArtifactPort artifacts,
            InteractionChannel interaction,
            Workspace workspace,
            EventPublisher events) {
    }

    private final EngineConfig config;
    private final Deps deps;
    private final ExecutorService toolPool;

    public TurnLoop(EngineConfig config, Deps deps, ExecutorService toolPool) {
        this.config = config;
        this.deps = deps;
        this.toolPool = toolPool;
    }

    /** 驱动一次 run。调用方已持有会话（RUNNING）。 */
    public RunResult run(Session session, String userInput) {
        Task task = Task.of(userInput);
        TurnContext turn = new TurnContext(session, task, deps.events(), config);
        // 步数上限按"单次 run"计：会话累计轮次由 SessionState 单独统计并随快照持久化
        int turnInRun = 0;
        try {
            // 用户输入：事件 → 账本 → 窗口
            deps.events().publish(new EngineEvent.UserMessage(session.id(), userInput));
            deps.ledger().append(session.id(), ChatMsg.user(userInput));
            session.window().addUser(userInput);

            while (true) {
                // ---- 检查点：中断 / 步数上限 ----
                if (session.isInterruptRequested()) {
                    return finish(session, StopCategory.INTERRUPTED, "用户中断");
                }
                if (turnInRun >= config.maxTurns()) {
                    return finish(session, StopCategory.MAX_TURNS, "本次运行达到步数上限 " + config.maxTurns());
                }
                int turnNo = turnInRun + 1;
                session.state().turnStarted();
                deps.events().publish(new EngineEvent.TurnStarted(session.id(), turnNo));

                // ---- 模型前：钩子（预算/压缩/…） ----
                HookOutcome pre = deps.hooks().runPreModel(turn);
                if (pre.stop().isPresent()) {
                    return finish(session, pre.stop().get().category(), pre.stop().get().message());
                }
                turn.nudges().addAll(pre.nudges()); // 本轮组装即生效
                if (session.isInterruptRequested()) {
                    return finish(session, StopCategory.INTERRUPTED, "用户中断");
                }

                // ---- 组装（零件由内核收集，布局由策略决定） ----
                LlmRequest request = new LlmRequest(
                        deps.assembler().assemble(parts(turn)),
                        deps.tools().specs());
                turn.nudges().clear(); // 提醒已消费

                // ---- 模型 ----
                LlmReply reply = callModel(session.id(), request);
                turn.setReply(reply);
                session.state().addUsage(reply.usage());
                deps.events().publish(new EngineEvent.UsageReported(session.id(), reply.usage()));
                if (reply.text() != null && !reply.text().isBlank()) {
                    deps.events().publish(new EngineEvent.AssistantMessage(session.id(), reply.text()));
                }
                deps.ledger().append(session.id(), ChatMsg.assistant(reply.text(), reply.thinking(), reply.toolCalls()));
                session.window().addAssistant(reply.text(), reply.thinking(), reply.toolCalls());

                // ---- 模型后：钩子（循环/无进展检测） ----
                HookOutcome post = deps.hooks().runPostModel(turn);
                if (post.stop().isPresent()) {
                    return finish(session, post.stop().get().category(), post.stop().get().message());
                }
                turn.nudges().addAll(post.nudges()); // 注入下一轮组装

                // ---- 正常退出条件：模型不再要工具 ----
                if (!reply.wantsTools()) {
                    return finish(session, StopCategory.COMPLETED, "正常完成");
                }

                // ---- 工具前：钩子（门禁审批） ----
                HookOutcome preTool = deps.hooks().runPreTool(turn, reply.toolCalls());
                if (preTool.stop().isPresent()) {
                    return finish(session, preTool.stop().get().category(), preTool.stop().get().message());
                }
                turn.nudges().addAll(preTool.nudges()); // 注入下一轮组装
                if (session.isInterruptRequested()) {
                    return finish(session, StopCategory.INTERRUPTED, "用户中断");
                }

                // ---- 工具执行（并行，事件全程可观测） ----
                List<ToolResult> results = executeTools(session, reply.toolCalls(), turnNo);

                // ---- 工具后：钩子（熔断计数/快照，纯观察） ----
                deps.hooks().runPostTool(turn, results);

                // ---- 溢出关卡 + 回填账本与窗口 ----
                long baseSeq = deps.ledger().size(session.id());
                for (int i = 0; i < results.size(); i++) {
                    ToolResult r = results.get(i);
                    String stored = spillIfHuge(r, baseSeq + i + 1);
                    ChatMsg msg = ChatMsg.toolResult(r.toolCallId(), r.toolName(), stored);
                    deps.ledger().append(session.id(), msg);
                    session.window().addToolResult(r.toolCallId(), r.toolName(), stored);
                }

                turnInRun++;
            }
        } catch (LlmPortException e) {
            return finish(session, StopCategory.INTERNAL_ERROR, "模型调用失败: " + e.getMessage());
        } catch (Exception e) {
            return finish(session, StopCategory.INTERNAL_ERROR, "内部错误: " + e);
        }
    }

    // ---- 组装零件收集 ----

    private AssemblyParts parts(TurnContext turn) {
        Session session = turn.session();
        return new AssemblyParts(
                config.systemPrompt(),
                safeRender(() -> deps.memory().render(turn.task().input(), config.memoryRenderMaxChars())),
                session.state().compression().lastSummary(),
                safeRender(() -> deps.environment().probe(session.id())),
                session.todos().render(),
                List.copyOf(turn.nudges()),
                session.window(),
                config.maxContextTokens());
    }

    /** 记忆/环境渲染失败不阻断循环（可观测性降级而非执行失败）。 */
    private String safeRender(java.util.function.Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "上下文零件渲染失败: " + e, e);
            return null;
        }
    }

    // ---- 模型调用 ----

    private LlmReply callModel(String sessionId, LlmRequest request) {
        LlmReply[] done = new LlmReply[1];
        LlmPortException[] failed = new LlmPortException[1];
        deps.llm().chat(request, new StreamCallbacks() {
            @Override
            public void onTextDelta(String delta) {
                deps.events().publish(new EngineEvent.AssistantTextDelta(sessionId, delta));
            }

            @Override
            public void onThinkingDelta(String delta) {
                deps.events().publish(new EngineEvent.AssistantThinkingDelta(sessionId, delta));
            }

            @Override
            public void onToolCallDelta(String toolCallId, String delta) {
                deps.events().publish(new EngineEvent.ToolCallArgsDelta(sessionId, toolCallId, delta));
            }

            @Override
            public void onDone(LlmReply reply) {
                done[0] = reply;
            }

            @Override
            public void onError(LlmPortException error) {
                failed[0] = error;
            }
        });
        if (failed[0] != null) {
            throw failed[0];
        }
        if (done[0] == null) {
            throw new LlmPortException("适配器未回调终点（onDone/onError）");
        }
        return done[0];
    }

    // ---- 工具执行 ----

    private List<ToolResult> executeTools(Session session, List<ToolCall> calls, int turnNo) {
        ToolContext ctx = new ToolContext(
                session.id(), turnNo, deps.workspace(), deps.artifacts(),
                deps.interaction(), session.todos(), deps.events());

        List<Callable<ToolResult>> jobs = new ArrayList<>(calls.size());
        for (ToolCall call : calls) {
            jobs.add(() -> {
                deps.events().publish(new EngineEvent.ToolExecutionStarted(session.id(), call.id(), call.name()));
                ToolResult result = executeWithGate(session, call, ctx);
                deps.events().publish(new EngineEvent.ToolExecutionFinished(session.id(), result));
                return result;
            });
        }
        try {
            List<Future<ToolResult>> futures = toolPool.invokeAll(jobs);
            List<ToolResult> out = new ArrayList<>(futures.size());
            for (Future<ToolResult> f : futures) {
                out.add(f.get());
            }
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return calls.stream().map(c -> ToolResult.failure(c, "执行被中断")).toList();
        } catch (java.util.concurrent.ExecutionException e) {
            // invokeAll 的任务体已捕获工具异常，这里只可能出基础设施错误
            return calls.stream().map(c -> ToolResult.failure(c, "执行基础设施错误: " + e.getCause())).toList();
        }
    }

    /** 破坏性工具走门禁；其余直接执行。 */
    private ToolResult executeWithGate(Session session, ToolCall call, ToolContext ctx) {
        if (deps.tools().isDestructive(call.name()) && !config.autoApproveDestructive()) {
            var spec = deps.tools().find(call.name()).map(t -> t.spec()).orElse(null);
            Decision decision = deps.interaction().gate(call, spec);
            if (decision == Decision.REJECTED) {
                return ToolResult.failure(call, "门禁拒绝：破坏性操作未获批准");
            }
        }
        return deps.tools().execute(call, ctx);
    }

    // ---- 溢出关卡 ----

    /** 超大工具结果落工件，窗口只留头尾 + 引用（上下文不被大结果淹没）。 */
    private String spillIfHuge(ToolResult r, long seq) {
        String output = r.output() == null ? "" : r.output();
        if (output.length() <= config.spillThresholdChars()) {
            return output;
        }
        ArtifactRef ref = deps.artifacts().spill(r.toolName(), output, seq);
        String head = output.substring(0, Math.min(config.spillKeepHeadChars(), output.length()));
        String tail = output.length() <= config.spillKeepTailChars()
                ? ""
                : output.substring(output.length() - config.spillKeepTailChars());
        return head
                + "\n…[结果过大已溢出，共 " + ref.totalLines() + " 行，完整内容引用 artifact:" + ref.refId()
                + "，可用 read 工具按行读回]…\n" + tail;
    }

    // ---- 收尾 ----

    private RunResult finish(Session session, StopCategory category, String message) {
        RunResult result = new RunResult(category, message, session.state().totalUsage());
        deps.events().publish(new EngineEvent.RunFinished(
                session.id(), category, message, session.state().totalUsage()));
        try {
            deps.snapshots().save(new SessionSnapshot(
                    session.id(),
                    session.todos().items(),
                    session.state().totalUsage(),
                    session.state().compression(),
                    session.state().turnCount(),
                    Instant.now()));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "快照保存失败: " + e, e);
        }
        return result;
    }
}
