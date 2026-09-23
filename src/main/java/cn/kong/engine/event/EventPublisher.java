package cn.kong.engine.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 事件发布器：同步、按发生顺序派发。
 * 订阅者异常被隔离（记日志，不中断循环）——可观测性不能反噬执行。
 */
public final class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final List<EventSink> sinks = new CopyOnWriteArrayList<>();

    public void subscribe(EventSink sink) {
        sinks.add(sink);
    }

    /** 退订 */
    public void unsubscribe(EventSink sink) {
        sinks.remove(sink);
    }

    public void publish(EngineEvent event) {
        for (EventSink sink : sinks) {
            try {
                sink.onEvent(event);
            } catch (Exception e) {
                log.warn("[Event] 订阅者异常 sink={}, event={}", sink.getClass().getName(), event.getClass().getSimpleName(), e);
            }
        }
    }
}
