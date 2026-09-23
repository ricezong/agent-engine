package cn.kong.engine.event;

import java.util.List;

import cn.kong.engine.msg.LlmUsage;
import cn.kong.engine.msg.ToolResult;
import cn.kong.engine.port.compression.CompressionLevel;
import cn.kong.engine.port.interaction.InteractionChannel.Question;
import cn.kong.engine.scope.SessionStatus;
import cn.kong.engine.scope.TodoItem;
import cn.kong.engine.stop.StopCategory;

/**
 * 引擎事件——内核的可观测性出口（只读流）。
 *
 * <p>密封类型：事件种类即引擎对外语义，新增事件=扩展此接口，
 * 所有订阅者编译期可见。事件按发生顺序同步派发。
 *
 * <p>分组：会话层 / 轮次层 / 模型层 / 工具层 / 交互层 / 治理层 / 收尾。
 */
public sealed interface EngineEvent permits
        EngineEvent.RunStarted,
        EngineEvent.SessionStatusChanged,
        EngineEvent.UserMessage,
        EngineEvent.TurnStarted,
        EngineEvent.AssistantTextDelta,
        EngineEvent.AssistantThinkingDelta,
        EngineEvent.ToolCallArgsDelta,
        EngineEvent.AssistantMessage,
        EngineEvent.UsageReported,
        EngineEvent.ToolExecutionStarted,
        EngineEvent.ToolExecutionFinished,
        EngineEvent.QuestionAsked,
        EngineEvent.TodoChanged,
        EngineEvent.GuardTriggered,
        EngineEvent.CompressionApplied,
        EngineEvent.RunFinished {

    // ---- 会话层 ----

    String sessionId();

    record RunStarted(String sessionId, boolean resumed) implements EngineEvent {
    }

    record SessionStatusChanged(String sessionId, SessionStatus status) implements EngineEvent {
    }

    // ---- 轮次层 ----

    record UserMessage(String sessionId, String text) implements EngineEvent {
    }

    record TurnStarted(String sessionId, int turn) implements EngineEvent {
    }

    // ---- 模型层 ----

    record AssistantTextDelta(String sessionId, String delta) implements EngineEvent {
    }

    record AssistantThinkingDelta(String sessionId, String delta) implements EngineEvent {
    }

    record ToolCallArgsDelta(String sessionId, String toolCallId, String delta) implements EngineEvent {
    }

    record AssistantMessage(String sessionId, String text) implements EngineEvent {
    }

    record UsageReported(String sessionId, LlmUsage usage) implements EngineEvent {
    }

    // ---- 工具层 ----

    record ToolExecutionStarted(String sessionId, String toolCallId, String toolName) implements EngineEvent {
    }

    record ToolExecutionFinished(String sessionId, ToolResult result) implements EngineEvent {
    }

    // ---- 交互层 ----

    record QuestionAsked(String sessionId, Question question) implements EngineEvent {
    }

    record TodoChanged(String sessionId, List<TodoItem> todos) implements EngineEvent {
    }

    // ---- 治理层（钩子/压缩） ----

    record GuardTriggered(String sessionId, String hook, String message, boolean stopped) implements EngineEvent {
    }

    record CompressionApplied(String sessionId, CompressionLevel level, long freedTokens) implements EngineEvent {
    }

    // ---- 收尾 ----

    record RunFinished(String sessionId, StopCategory category, String message, LlmUsage totalUsage) implements EngineEvent {
    }
}
