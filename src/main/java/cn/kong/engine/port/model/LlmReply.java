package cn.kong.engine.port.model;

import java.util.List;

import cn.kong.engine.msg.LlmUsage;
import cn.kong.engine.msg.ToolCall;

/** 模型一轮的完整回复。toolCalls 为空即循环的正常退出条件。 */
public record LlmReply(String text, String thinking, List<ToolCall> toolCalls, LlmUsage usage) {

    public LlmReply {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? LlmUsage.ZERO : usage;
    }

    public boolean wantsTools() {
        return !toolCalls.isEmpty();
    }
}
