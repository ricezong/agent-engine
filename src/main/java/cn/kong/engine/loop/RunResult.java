package cn.kong.engine.loop;

import cn.kong.engine.msg.LlmUsage;
import cn.kong.engine.stop.StopCategory;

/** 运行结果：终止分类 + 说明 + 会话累计用量。 */
public record RunResult(StopCategory category, String message, LlmUsage totalUsage) {
}
