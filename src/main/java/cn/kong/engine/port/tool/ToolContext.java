package cn.kong.engine.port.tool;

import cn.kong.engine.event.EventPublisher;
import cn.kong.engine.port.interaction.InteractionChannel;
import cn.kong.engine.port.store.ArtifactPort;
import cn.kong.engine.port.store.Workspace;
import cn.kong.engine.scope.TodoBoard;

/**
 * 工具执行的会话级上下文——引擎在每次工具执行时注入。
 * 工件读写、人机交互、待办更新、进度事件都从这里拿。
 */
public record ToolContext(
        String sessionId,
        int turn,
        Workspace workspace,
        ArtifactPort artifacts,
        InteractionChannel interaction,
        TodoBoard todos,
        EventPublisher events) {
}
