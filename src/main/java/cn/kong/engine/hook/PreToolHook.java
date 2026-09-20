package cn.kong.engine.hook;

import java.util.List;

import cn.kong.engine.loop.TurnContext;
import cn.kong.engine.msg.ToolCall;

/** 工具执行前：门禁审批（gate）发生在这里。 */
public interface PreToolHook extends Hook {

    HookResult beforeTools(TurnContext turn, List<ToolCall> calls);
}
