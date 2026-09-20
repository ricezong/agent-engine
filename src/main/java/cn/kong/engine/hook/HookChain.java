package cn.kong.engine.hook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import cn.kong.engine.event.EngineEvent;
import cn.kong.engine.event.EventPublisher;
import cn.kong.engine.loop.TurnContext;
import cn.kong.engine.msg.ToolCall;
import cn.kong.engine.msg.ToolResult;

/**
 * 钩子链：按 order 排序执行四阶段钩子，聚合结论。
 *
 * <p>规则：Nudge 累积（注入下轮组装）；Stop 立即生效（首个 Stop 优先，其后不再执行）。
 * 所有 Nudge/Stop 都发布 GuardTriggered 事件——治理行为天然可观测。
 */
public final class HookChain {

    private final List<Hook> hooks;
    private final EventPublisher events;

    public HookChain(List<Hook> hooks, EventPublisher events) {
        this.hooks = hooks.stream().sorted(Comparator.comparingInt(Hook::order)).toList();
        this.events = events;
    }

    public HookOutcome runPreModel(TurnContext turn) {
        List<String> nudges = new ArrayList<>();
        for (Hook hook : hooks) {
            if (hook instanceof PreModelHook h) {
                Optional<HookResult.Stop> stop = aggregate(hook, h.beforeModel(turn), turn, nudges);
                if (stop.isPresent()) {
                    return new HookOutcome(List.copyOf(nudges), stop);
                }
            }
        }
        return new HookOutcome(List.copyOf(nudges), Optional.empty());
    }

    public HookOutcome runPostModel(TurnContext turn) {
        List<String> nudges = new ArrayList<>();
        for (Hook hook : hooks) {
            if (hook instanceof PostModelHook h) {
                Optional<HookResult.Stop> stop = aggregate(hook, h.afterModel(turn), turn, nudges);
                if (stop.isPresent()) {
                    return new HookOutcome(List.copyOf(nudges), stop);
                }
            }
        }
        return new HookOutcome(List.copyOf(nudges), Optional.empty());
    }

    public HookOutcome runPreTool(TurnContext turn, List<ToolCall> calls) {
        List<String> nudges = new ArrayList<>();
        for (Hook hook : hooks) {
            if (hook instanceof PreToolHook h) {
                Optional<HookResult.Stop> stop = aggregate(hook, h.beforeTools(turn, calls), turn, nudges);
                if (stop.isPresent()) {
                    return new HookOutcome(List.copyOf(nudges), stop);
                }
            }
        }
        return new HookOutcome(List.copyOf(nudges), Optional.empty());
    }

    public void runPostTool(TurnContext turn, List<ToolResult> results) {
        for (Hook hook : hooks) {
            if (hook instanceof PostToolHook h) {
                h.afterTools(turn, results);
            }
        }
    }

    /** 聚合单个钩子结论：Nudge 记账；Stop 发事件并返回终止。 */
    private Optional<HookResult.Stop> aggregate(Hook hook, HookResult result, TurnContext turn, List<String> nudges) {
        if (result instanceof HookResult.Nudge n) {
            events.publish(new EngineEvent.GuardTriggered(turn.sessionId(), hook.name(), n.message(), false));
            nudges.add(n.message());
            return Optional.empty();
        }
        if (result instanceof HookResult.Stop s) {
            events.publish(new EngineEvent.GuardTriggered(turn.sessionId(), hook.name(), s.message(), true));
            return Optional.of(s);
        }
        return Optional.empty();
    }

    /** 一次阶段执行的聚合结论。 */
    public record HookOutcome(List<String> nudges, Optional<HookResult.Stop> stop) {
    }
}
