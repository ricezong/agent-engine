package cn.kong.engine.port.compression;

/** 压缩档位。NONE 不动；SNIP 截断最老工具结果；PRUNE 清空历史换摘要位；SUMMARIZE 全量换摘要。 */
public enum CompressionLevel {
    NONE, SNIP, PRUNE, SUMMARIZE
}
