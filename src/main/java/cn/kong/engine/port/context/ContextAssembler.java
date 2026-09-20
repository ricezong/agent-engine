package cn.kong.engine.port.context;

import java.util.List;

import cn.kong.engine.msg.ChatMsg;
import cn.kong.engine.window.ContextWindow;

/**
 * 上下文组装策略：拿到引擎收集好的全部"零件"，决定最终发给模型的消息布局。
 *
 * <p>分工：内核逐轮收集零件（记忆/环境/摘要/待办/提醒），策略只管布局。
 */
public interface ContextAssembler {

    List<ChatMsg> assemble(AssemblyParts parts);

    /** 组装零件（由内核收集）。null/空字段表示该段缺省、不注入。 */
    record AssemblyParts(
            String systemPrompt,
            String memories,
            String summary,
            String environment,
            String todo,
            List<String> nudges,
            ContextWindow window,
            long tokenBudget) {

        public AssemblyParts {
            nudges = nudges == null ? List.of() : List.copyOf(nudges);
        }
    }
}
