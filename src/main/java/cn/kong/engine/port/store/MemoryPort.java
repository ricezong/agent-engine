package cn.kong.engine.port.store;

/** 跨会话记忆端口：组装时渲染进上下文头部，update_memory 工具写入。 */
public interface MemoryPort {

    /** 渲染为注入文本；无记忆返回 null。 */
    String render(String query, int maxChars);

    void upsert(String id, String content);

    void delete(String id);
}
