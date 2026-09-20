package cn.kong.engine.msg;

/**
 * 工具规格：暴露给模型的工具描述。
 *
 * @param name                 工具名（模型据此调用）
 * @param description          功能描述（写给模型看）
 * @param parametersJsonSchema JSON Schema 形式的参数描述；空 schema 表示无参数
 * @param destructive          是否破坏性操作（删除/覆盖等），默认走门禁审批
 */
public record ToolSpec(String name, String description, String parametersJsonSchema, boolean destructive) {

    private static final String EMPTY_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    public static ToolSpec of(String name, String description) {
        return new ToolSpec(name, description, EMPTY_SCHEMA, false);
    }

    public static ToolSpec destructive(String name, String description) {
        return new ToolSpec(name, description, EMPTY_SCHEMA, true);
    }
}
