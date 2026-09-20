package cn.kong.engine.scope;

import java.util.List;

import cn.kong.engine.event.EngineEvent;
import cn.kong.engine.event.EventPublisher;

/**
 * 待办板：会话级待办状态（随快照持久化），变更即发 TodoChanged 事件。
 * 工具经 ToolContext.todos() 更新；组装层经 render() 注入上下文。
 */
public final class TodoBoard {

    private final String sessionId;
    private final EventPublisher events;
    private volatile List<TodoItem> items = List.of();

    public TodoBoard(String sessionId, EventPublisher events) {
        this.sessionId = sessionId;
        this.events = events;
    }

    public void replace(List<TodoItem> next) {
        items = next == null ? List.of() : List.copyOf(next);
        events.publish(new EngineEvent.TodoChanged(sessionId, items()));
    }

    /** 快照恢复用：不发事件的静默替换。 */
    public void restore(List<TodoItem> saved) {
        items = saved == null ? List.of() : List.copyOf(saved);
    }

    public List<TodoItem> items() {
        return items;
    }

    /** 渲染为注入上下文的文本；空返回 null。 */
    public String render() {
        if (items.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (TodoItem t : items) {
            sb.append(i++).append(". [").append(t.status()).append("] ").append(t.content()).append('\n');
        }
        return sb.toString();
    }
}
