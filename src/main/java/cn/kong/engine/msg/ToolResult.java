package cn.kong.engine.msg;

/**
 * 工具执行结果：回填给模型的 TOOL 消息内容。
 *
 * <p>注意：失败不等于中断。失败结果同样回填给模型，由模型决定下一步；
 * 只有钩子（门禁/预算等）才能终止循环。
 */
public record ToolResult(String toolCallId, String toolName, boolean success, String output) {

    public static ToolResult success(ToolCall call, String output) {
        return new ToolResult(call.id(), call.name(), true, output);
    }

    public static ToolResult failure(ToolCall call, String reason) {
        return new ToolResult(call.id(), call.name(), false, "执行失败: " + reason);
    }
}
