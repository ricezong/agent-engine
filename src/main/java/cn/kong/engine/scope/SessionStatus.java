package cn.kong.engine.scope;

/** 会话状态机：IDLE → RUNNING →（WAITING_ASK）→ IDLE；中断经 STOPPING 收尾。 */
public enum SessionStatus {
    IDLE, RUNNING, WAITING_ASK, STOPPING, CLOSED
}
