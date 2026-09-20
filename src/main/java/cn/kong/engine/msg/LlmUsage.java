package cn.kong.engine.msg;

/** 一次模型调用的 token 用量（预算守护的计量依据）。 */
public record LlmUsage(long inputTokens, long outputTokens) {

    public static final LlmUsage ZERO = new LlmUsage(0, 0);

    public long total() {
        return inputTokens + outputTokens;
    }

    public LlmUsage plus(LlmUsage other) {
        return new LlmUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens);
    }
}
