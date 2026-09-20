package cn.kong.engine.port.model;

import java.util.List;

import cn.kong.engine.msg.ChatMsg;
import cn.kong.engine.msg.ToolSpec;

/** 一次模型请求：组装后的消息 + 可用工具集。 */
public record LlmRequest(List<ChatMsg> messages, List<ToolSpec> tools) {

    public LlmRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
