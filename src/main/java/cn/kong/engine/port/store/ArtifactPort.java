package cn.kong.engine.port.store;

/** 工件端口：工具超大结果的落盘与读回（溢出关卡）。 */
public interface ArtifactPort {

    /** 落盘并返回引用。seq 用于生成跨重放稳定的引用 ID。 */
    ArtifactRef spill(String sessionId, String toolName, String content, long seq);

    /** 按行区间读回。 */
    String read(String sessionId, String refId, int offset, int limit);

    record ArtifactRef(String refId, int totalLines) {
    }
}
