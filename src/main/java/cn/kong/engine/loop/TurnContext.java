package cn.kong.engine.loop;

import java.util.ArrayList;
import java.util.List;

import cn.kong.engine.EngineConfig;
import cn.kong.engine.event.EventPublisher;
import cn.kong.engine.port.model.LlmReply;
import cn.kong.engine.scope.Session;
import cn.kong.engine.scope.Task;
import cn.kong.engine.window.ContextWindow;

/**
 * 一轮（turn）的上下文——run 级实例、逐轮复用。
 *
 * <p>钩子通过它读写运行中状态：
 * reply 在模型返回后填充（PostModel 钩子可读）；nudges 累积注入下一轮组装。
 */
public final class TurnContext {

    private final Session session;
    private final Task task;
    private final EventPublisher events;
    private final EngineConfig config;
    private final List<String> nudges = new ArrayList<>();
    private LlmReply reply;

    public TurnContext(Session session, Task task, EventPublisher events, EngineConfig config) {
        this.session = session;
        this.task = task;
        this.events = events;
        this.config = config;
    }

    public Session session() {
        return session;
    }

    public Task task() {
        return task;
    }

    public EventPublisher events() {
        return events;
    }

    public EngineConfig config() {
        return config;
    }

    public ContextWindow window() {
        return session.window();
    }

    public String sessionId() {
        return session.id();
    }

    public LlmReply reply() {
        return reply;
    }

    public void setReply(LlmReply reply) {
        this.reply = reply;
    }

    /** 累积的温和提醒（钩子写入；组装后由循环清空）。 */
    public List<String> nudges() {
        return nudges;
    }
}
