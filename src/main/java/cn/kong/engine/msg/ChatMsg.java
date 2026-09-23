package cn.kong.engine.msg;

import java.util.List;

/**
 * 引擎自有消息模型——与任何 LLM SDK 解耦，各供应商适配器负责双向翻译。
 *
 * <p>四种角色：
 * <ul>
 *   <li>SYSTEM：系统/记忆/环境等头尾注入段</li>
 *   <li>USER：用户输入</li>
 *   <li>ASSISTANT：模型回复（可同时携带文本、思维链、工具调用）</li>
 *   <li>TOOL：工具执行结果（携带 toolCallId 与对应工具名，配对回填）</li>
 * </ul>
 */
public record ChatMsg(
        Role role,
        String text,
        String thinking,
        List<ToolCall> toolCalls,
        String toolCallId,
        String toolName,
        String toolView) {

    public ChatMsg {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatMsg system(String text) {
        return new ChatMsg(Role.SYSTEM, text, null, null, null, null, null);
    }

    public static ChatMsg user(String text) {
        return new ChatMsg(Role.USER, text, null, null, null, null, null);
    }

    public static ChatMsg assistant(String text, String thinking, List<ToolCall> toolCalls) {
        return new ChatMsg(Role.ASSISTANT, text, thinking, toolCalls, null, null, null);
    }

    public static ChatMsg toolResult(String toolCallId, String toolName, String text, String toolView) {
        return new ChatMsg(Role.TOOL, text, null, null, toolCallId, toolName, toolView);
    }
}
