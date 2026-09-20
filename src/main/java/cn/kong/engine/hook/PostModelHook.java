package cn.kong.engine.hook;

import cn.kong.engine.loop.TurnContext;

/** 模型回复后：循环检测、无进展检测发生在这里。 */
public interface PostModelHook extends Hook {

    HookResult afterModel(TurnContext turn);
}
