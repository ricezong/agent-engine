package cn.kong.engine.port.compression;

/** 压缩状态（会话级，随快照持久化）。 */
public record CompressionState(String lastSummary, int lastCompressedTurn, int summarizeCount) {

    public static final CompressionState EMPTY = new CompressionState(null, -1, 0);
}
