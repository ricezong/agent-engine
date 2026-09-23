package cn.kong.engine;

import java.time.Duration;

/**
 * 引擎配置（不可变，Builder 构建）。
 *
 * <p>只放引擎行为参数；模型参数（temperature 等）属于 LLM 适配器自身配置。
 */
public final class EngineConfig {

    // ---- 循环 ----
    private final int maxTurns;                 // 单次 run 最大轮次（默认 30）
    private final int toolParallelism;          // 工具并行度（默认 4）
    private final Duration toolTimeout;         // 单个工具执行上限（默认 30 秒）
    private final boolean autoApproveDestructive; // 破坏性工具免门禁（默认 false，生产勿开）

    // ---- 上下文 ----
    private final String systemPrompt;          // 系统提示词
    private final long maxContextTokens;        // 上下文预算（默认 200_000）
    private final int tailGuardBlocks;          // 尾部保护块数（压缩不许动，默认 12）
    private final int spillThresholdChars;      // 工具结果溢出阈值（默认 12_000）
    private final int spillKeepHeadChars;       // 溢出保留头（默认 600）
    private final int spillKeepTailChars;       // 溢出保留尾（默认 400）
    private final int memoryRenderMaxChars;     // 记忆渲染上限（默认 2_000）

    private EngineConfig(Builder b) {
        this.maxTurns = b.maxTurns;
        this.toolParallelism = b.toolParallelism;
        this.toolTimeout = b.toolTimeout;
        this.autoApproveDestructive = b.autoApproveDestructive;
        this.systemPrompt = b.systemPrompt;
        this.maxContextTokens = b.maxContextTokens;
        this.tailGuardBlocks = b.tailGuardBlocks;
        this.spillThresholdChars = b.spillThresholdChars;
        this.spillKeepHeadChars = b.spillKeepHeadChars;
        this.spillKeepTailChars = b.spillKeepTailChars;
        this.memoryRenderMaxChars = b.memoryRenderMaxChars;
    }

    public static EngineConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int maxTurns() {
        return maxTurns;
    }

    public int toolParallelism() {
        return toolParallelism;
    }

    public Duration toolTimeout() {
        return toolTimeout;
    }

    public boolean autoApproveDestructive() {
        return autoApproveDestructive;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public long maxContextTokens() {
        return maxContextTokens;
    }

    public int tailGuardBlocks() {
        return tailGuardBlocks;
    }

    public int spillThresholdChars() {
        return spillThresholdChars;
    }

    public int spillKeepHeadChars() {
        return spillKeepHeadChars;
    }

    public int spillKeepTailChars() {
        return spillKeepTailChars;
    }

    public int memoryRenderMaxChars() {
        return memoryRenderMaxChars;
    }

    /** 显式构建器：所有默认值一眼可见。 */
    public static final class Builder {

        private int maxTurns = 30;
        private int toolParallelism = 4;
        private Duration toolTimeout = Duration.ofSeconds(30);
        private boolean autoApproveDestructive = false;
        private String systemPrompt = "你是一个能调用工具的助手，按需使用工具完成任务。";
        private long maxContextTokens = 200_000;
        private int tailGuardBlocks = 12;
        private int spillThresholdChars = 12_000;
        private int spillKeepHeadChars = 600;
        private int spillKeepTailChars = 400;
        private int memoryRenderMaxChars = 2_000;

        public Builder maxTurns(int v) {
            this.maxTurns = v;
            return this;
        }

        public Builder toolParallelism(int v) {
            this.toolParallelism = v;
            return this;
        }

        public Builder toolTimeout(Duration v) {
            this.toolTimeout = v;
            return this;
        }

        public Builder autoApproveDestructive(boolean v) {
            this.autoApproveDestructive = v;
            return this;
        }

        public Builder systemPrompt(String v) {
            this.systemPrompt = v;
            return this;
        }

        public Builder maxContextTokens(long v) {
            this.maxContextTokens = v;
            return this;
        }

        public Builder tailGuardBlocks(int v) {
            this.tailGuardBlocks = v;
            return this;
        }

        public Builder spillThresholdChars(int v) {
            this.spillThresholdChars = v;
            return this;
        }

        public Builder spillKeepHeadChars(int v) {
            this.spillKeepHeadChars = v;
            return this;
        }

        public Builder spillKeepTailChars(int v) {
            this.spillKeepTailChars = v;
            return this;
        }

        public Builder memoryRenderMaxChars(int v) {
            this.memoryRenderMaxChars = v;
            return this;
        }

        public EngineConfig build() {
            return new EngineConfig(this);
        }
    }
}
