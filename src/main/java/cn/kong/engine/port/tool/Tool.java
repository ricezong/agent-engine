package cn.kong.engine.port.tool;

import cn.kong.engine.msg.ToolCall;
import cn.kong.engine.msg.ToolResult;
import cn.kong.engine.msg.ToolSpec;

/**
 * 单个工具。
 *
 * <p>参数以原始 JSON 字符串传入（call.argumentsJson()）——内核零依赖不解析 JSON，
 * modules 层可提供基于 Jackson 的抽象基类做参数解析。
 */
public interface Tool {

    ToolSpec spec();

    /** 执行工具。实现应返回 failure 结果表达失败，而非向外抛异常（循环不因工具失败中断）。 */
    ToolResult execute(ToolCall call, ToolContext ctx);
}
