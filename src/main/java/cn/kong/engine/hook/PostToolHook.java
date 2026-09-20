package cn.kong.engine.hook;

import java.util.List;

import cn.kong.engine.loop.TurnContext;
import cn.kong.engine.msg.ToolResult;

/** 工具执行后：熔断计数、快照落盘发生在这里。纯观察，不能终止。 */
public interface PostToolHook extends Hook {

    void afterTools(TurnContext turn, List<ToolResult> results);
}
