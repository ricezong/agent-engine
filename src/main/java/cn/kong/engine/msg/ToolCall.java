package cn.kong.engine.msg;

/**
 * 模型发起的一次工具调用。
 *
 * @param id            调用 ID（模型生成，用于配对工具结果）
 * @param name          工具名
 * @param argumentsJson 原始参数 JSON 字符串——内核零依赖不解析，由工具自行处理
 */
public record ToolCall(String id, String name, String argumentsJson) {
}
