package cn.kong.engine.port.context;

import java.util.Map;

import cn.kong.engine.window.BlockKind;

/** 上下文指标：窗口用量快照，供压缩策略决策。 */
public record ContextMetrics(long usedTokens, long budgetTokens, Map<BlockKind, Long> byKind, int protectedTailBlocks) {
}
