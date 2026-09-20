package cn.kong.engine.hook;

import cn.kong.engine.loop.TurnContext;

/** 模型调用前：预算检查、上下文压缩发生在这里。 */
public interface PreModelHook extends Hook {

    HookResult beforeModel(TurnContext turn);
}
