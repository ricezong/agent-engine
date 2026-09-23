package cn.kong.engine.window;

import java.util.List;

import cn.kong.engine.msg.ToolCall;

/**
 * 窗口块：一段不可变的会话历史。
 * ASSISTANT 块可同时携带 text/thinking/toolCalls；TOOL_RESULT 块携带 toolCallId/toolName/toolView
 */
public record Block(
        BlockKind kind,
        String text,
        String thinking,
        List<ToolCall> toolCalls,
        String toolCallId,
        String toolName,
        String toolView) {

    public Block {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Block user(String text) {
        return new Block(BlockKind.USER, text, null, null, null, null, null);
    }

    public static Block assistant(String text, String thinking, List<ToolCall> toolCalls) {
        return new Block(BlockKind.ASSISTANT, text, thinking, toolCalls, null, null, null);
    }

    public static Block toolResult(String toolCallId, String toolName, String text, String toolView) {
        return new Block(BlockKind.TOOL_RESULT, text, null, null, toolCallId, toolName, toolView);
    }
}
