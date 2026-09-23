package cn.kong.engine.port.model;

import java.util.List;

import cn.kong.engine.msg.LlmUsage;
import cn.kong.engine.msg.ToolCall;

/**
 * 模型一轮的完整回复。toolCalls 为空即循环的正常退出条件（截断除外）。
 *
 * @param text         回复文本（可为 null）
 * @param thinking     思维链文本（模型不支持时为 null）
 * @param toolCalls    模型发起的工具调用
 * @param usage        本轮 token 用量
 * @param finishReason 结束原因（openai 风格：stop/length/tool_calls…）。length 表示输出被截断——循环将续写而非退出，续写提示由应用层钩子注入。
 */
public record LlmReply(String text, String thinking, List<ToolCall> toolCalls, LlmUsage usage, String finishReason) {

    public boolean wantsTools() {
        return !toolCalls.isEmpty();
    }

    /** 输出因长度上限被截断。 */
    public boolean truncated() {
        return "length".equalsIgnoreCase(finishReason);
    }
}
