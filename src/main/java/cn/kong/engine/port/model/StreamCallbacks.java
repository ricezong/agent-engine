package cn.kong.engine.port.model;

/** LLM 流式回调——所有增量由引擎转发到事件总线；终点是 onDone 或 onError。 */
public interface StreamCallbacks {

    void onTextDelta(String delta);

    void onThinkingDelta(String delta);

    void onToolCallDelta(String toolCallId, String delta);

    /** 正常终点。 */
    void onDone(LlmReply reply);

    /** 失败终点。回调此方法后不得再触发其他回调。 */
    void onError(LlmPortException error);
}
