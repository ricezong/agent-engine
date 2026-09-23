package cn.kong.engine.port.compression;

import cn.kong.engine.port.context.ContextMetrics;
import cn.kong.engine.window.ContextWindow;

/**
 * 压缩策略端口。
 */
public interface CompressionPolicy {

    CompressionLevel apply(ContextWindow window, ContextMetrics metrics, CompressionState state, int turnCount);
}
