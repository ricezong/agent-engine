package cn.kong.engine.port.model;

import java.util.List;

import cn.kong.engine.msg.ChatMsg;

/**
 * LLM 端口——引擎对"模型"的全部认知。
 *
 * <p>实现约定：
 * <ul>
 *   <li>chat() 为同步阻塞调用：在调用线程上按序触发回调，onDone/onError 为终点</li>
 *   <li>网络重试在适配器内部完成（指数退避），最终失败才走 onError</li>
 *   <li>模型参数（temperature 等）属于适配器自身配置，不经引擎传递</li>
 * </ul>
 */
public interface LlmPort {

    /** 流式对话：按序回调文本/思维链/工具参数增量，结束于 onDone 或 onError。 */
    void chat(LlmRequest request, StreamCallbacks callbacks);

    /** 同步纯文本补全（摘要器等内部用途，不带工具）。 */
    String complete(List<ChatMsg> messages);
}
