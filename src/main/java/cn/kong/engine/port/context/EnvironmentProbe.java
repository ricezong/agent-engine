package cn.kong.engine.port.context;

/** 环境信息探针：每轮收集（工作目录/时间/任务提醒等），返回 null 表示无。 */
@FunctionalInterface
public interface EnvironmentProbe {

    String probe(String sessionId);
}
