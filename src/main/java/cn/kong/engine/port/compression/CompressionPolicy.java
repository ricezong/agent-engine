package cn.kong.engine.port.compression;

import cn.kong.engine.port.context.ContextMetrics;
import cn.kong.engine.window.ContextWindow;

/**
 * 压缩策略端口。
 *
 * <p>引擎的压缩钩子（modules 层，P3）在 PreModel 阶段调用本策略；
 * 策略可直接改写窗口（截断/清空），SUMMARIZE 档位所需的摘要器由策略实现自持。
 * 窗口的压缩操作集（截断/替换历史）在 P3 随 WatermarkCompression 一起扩充。
 */
public interface CompressionPolicy {

    CompressionLevel apply(ContextWindow window, ContextMetrics metrics, CompressionState state, int turnCount);
}
