package cn.kong.engine.window;

/**
 * 会话历史块类型。
 *
 * <p>窗口只存对话（用户/助手/工具结果）；系统/记忆/环境等头尾注入段
 * 每轮由组装器现拼，不进窗口。
 */
public enum BlockKind {
    USER, ASSISTANT, TOOL_RESULT
}
