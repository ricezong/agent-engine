package cn.kong.engine.scope;

/** 单条待办（会话级状态，随快照持久化）。 */
public record TodoItem(String content, Status status, int addedTurn) {

    public enum Status {
        TODO, IN_PROGRESS, DONE
    }
}
