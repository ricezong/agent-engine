package cn.kong.engine.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件发布器：同步、按发生顺序派发。
 * 订阅者异常被隔离（记日志，不中断循环）——可观测性不能反噬执行。
 */
public final class EventPublisher {

    private static final System.Logger LOG = System.getLogger(EventPublisher.class.getName());

    private final List<EventSink> sinks = new CopyOnWriteArrayList<>();

    public void subscribe(EventSink sink) {
        sinks.add(sink);
    }

    public void publish(EngineEvent event) {
        for (EventSink sink : sinks) {
            try {
                sink.onEvent(event);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING, "事件订阅者异常: " + e, e);
            }
        }
    }
}
