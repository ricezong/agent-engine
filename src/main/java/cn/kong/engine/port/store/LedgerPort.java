package cn.kong.engine.port.store;

import java.util.List;

import cn.kong.engine.msg.ChatMsg;

/**
 * 只追加账本端口：会话消息的持久化事实源（按会话隔离）。
 * 恢复会话 = 账本重放重建窗口 + 快照恢复状态。
 */
public interface LedgerPort {

    /** 向指定会话追加一条消息，返回该会话内递增序号。 */
    long append(String sessionId, ChatMsg msg);

    /** 重放指定会话 fromSeq（含）之后的全部记录。 */
    List<LedgerRecord> replay(String sessionId, long fromSeq);

    /** 指定会话的当前记录条数。 */
    long size(String sessionId);

    record LedgerRecord(long seq, ChatMsg msg) {
    }
}
