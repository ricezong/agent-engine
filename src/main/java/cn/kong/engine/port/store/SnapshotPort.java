package cn.kong.engine.port.store;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import cn.kong.engine.msg.LlmUsage;
import cn.kong.engine.port.compression.CompressionState;
import cn.kong.engine.scope.TodoItem;

/** 终态快照端口：保存/恢复会话级状态（待办、累计用量、压缩状态、轮次）。 */
public interface SnapshotPort {

    void save(SessionSnapshot snapshot);

    Optional<SessionSnapshot> load(String sessionId);

    record SessionSnapshot(
            String sessionId,
            List<TodoItem> todos,
            LlmUsage totalUsage,
            CompressionState compression,
            int turnCount,
            Instant savedAt) {

        public SessionSnapshot {
            todos = todos == null ? List.of() : List.copyOf(todos);
        }
    }
}
