package cn.kong.engine.port.tool;

import java.util.List;
import java.util.Optional;

import cn.kong.engine.msg.ToolCall;
import cn.kong.engine.msg.ToolResult;
import cn.kong.engine.msg.ToolSpec;

/**
 * 工具集合端口——引擎只认这个聚合入口。
 *
 * <p>统一执行入口保证：未知工具、实现异常都转为 failure 结果回填给模型（不中断循环）。
 */
public interface ToolSet {

    /** 暴露给模型的全部工具规格。 */
    List<ToolSpec> specs();

    /** 按名查找工具。 */
    Optional<Tool> find(String name);

    /** 工具是否破坏性（默认依据 spec.destructive）。 */
    default boolean isDestructive(String toolName) {
        return find(toolName).map(t -> t.spec().destructive()).orElse(false);
    }

    /** 统一执行入口：查不到或实现抛异常 → failure 结果。 */
    default ToolResult execute(ToolCall call, ToolContext ctx) {
        Optional<Tool> found = find(call.name());
        if (found.isEmpty()) {
            return ToolResult.failure(call, "未知工具: " + call.name());
        }
        try {
            return found.get().execute(call, ctx);
        } catch (Exception e) {
            return ToolResult.failure(call, "工具执行异常: " + e);
        }
    }
}
