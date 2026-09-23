package cn.kong.engine.window;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.kong.engine.msg.ChatMsg;
import cn.kong.engine.msg.Role;
import cn.kong.engine.msg.ToolCall;
import cn.kong.engine.port.context.ContextMetrics;
import cn.kong.engine.port.context.TokenEstimator;

/**
 * 上下文窗口：会话历史的内存结构。
 *
 * <p>核心不变量（配对完整性）：每个 assistant.toolCall 必有配对的 TOOL_RESULT 块，
 * 孤儿结果被丢弃。toMessages() 出口做配对修复，保证发给模型的消息序列合法
 */
public final class ContextWindow {

    private final List<Block> blocks = new ArrayList<>();
    private final TokenEstimator estimator;

    public ContextWindow(TokenEstimator estimator) {
        this.estimator = estimator;
    }

    // ---- 写入 ----

    public void addUser(String text) {
        blocks.add(Block.user(text));
    }

    public void addAssistant(String text, String thinking, List<ToolCall> toolCalls) {
        blocks.add(Block.assistant(text, thinking, toolCalls));
    }

    public void addToolResult(String toolCallId, String toolName, String text, String toolView) {
        blocks.add(Block.toolResult(toolCallId, toolName, text, toolView));
    }

    /** 账本重放：由历史消息重建窗口（SYSTEM 头尾段不进窗口）。 */
    public void addFromMessage(ChatMsg msg) {
        switch (msg.role()) {
            case USER -> addUser(msg.text());
            case ASSISTANT -> addAssistant(msg.text(), msg.thinking(), msg.toolCalls());
            case TOOL -> addToolResult(msg.toolCallId(), msg.toolName(), msg.text(), msg.toolView());
            default -> { /* SYSTEM 忽略 */ }
        }
    }

    // ---- 读取 ----

    public List<Block> blocks() {
        return List.copyOf(blocks);
    }

    public int size() {
        return blocks.size();
    }

    /** 转为模型消息序列（含配对修复）。 */
    public List<ChatMsg> toMessages() {
        List<ChatMsg> raw = new ArrayList<>(blocks.size());
        for (Block b : blocks) {
            switch (b.kind()) {
                case USER -> raw.add(ChatMsg.user(b.text()));
                case ASSISTANT -> raw.add(ChatMsg.assistant(b.text(), b.thinking(), b.toolCalls()));
                case TOOL_RESULT -> raw.add(ChatMsg.toolResult(b.toolCallId(), b.toolName(), b.text(), b.toolView()));
            }
        }
        return repairPairing(raw);
    }

    // ---- 压缩操作（压缩策略/钩子调用；普通轮次不触碰） ----

    /**
     * SNIP：截断旧工具结果文本（保留头尾），尾部 keepTailBlocks 块不动。
     * 返回释放的粗估 token。
     */
    public long snipOldToolResults(int keepTailBlocks, int keepHeadChars, int keepTailChars) {
        long freed = 0;
        int from = Math.max(0, blocks.size() - Math.max(1, keepTailBlocks));
        for (int i = 0; i < from; i++) {
            Block b = blocks.get(i);
            String text = b.text();
            if (b.kind() == BlockKind.TOOL_RESULT
                    && text != null && text.length() > keepHeadChars + keepTailChars + 32) {
                String snipped = text.substring(0, keepHeadChars)
                        + "\n…（中间内容已截断）…\n"
                        + text.substring(text.length() - keepTailChars);
                freed += estimator.estimate(text) - estimator.estimate(snipped);
                // toolView 是 UI 元数据，不随正文截断
                blocks.set(i, Block.toolResult(b.toolCallId(), b.toolName(), snipped, b.toolView()));
            }
        }
        return freed;
    }

    /**
     * PRUNE/SUMMARIZE：仅保留最近 keepTailBlocks 块，更早历史整体移除。
     * 配对完整性由 toMessages() 的修复出口兜底（孤儿结果自动丢弃）。
     * 返回移除的粗估 token。
     */
    public long clearBefore(int keepTailBlocks) {
        int remove = Math.max(0, blocks.size() - Math.max(1, keepTailBlocks));
        if (remove == 0) {
            return 0;
        }
        long freed = 0;
        for (int i = 0; i < remove; i++) {
            freed += estimator.estimate(roughText(blocks.get(i)));
        }
        blocks.subList(0, remove).clear();
        return freed;
    }

    private String roughText(Block b) {
        StringBuilder sb = new StringBuilder();
        if (b.text() != null) {
            sb.append(b.text());
        }
        if (b.thinking() != null) {
            sb.append(b.thinking());
        }
        for (ToolCall c : b.toolCalls()) {
            sb.append(c.name()).append(c.argumentsJson());
        }
        return sb.toString();
    }

    /** 指标：token 用量与块分布（供压缩策略）。 */
    public ContextMetrics metrics(long budgetTokens, int protectedTailBlocks) {
        long used = 0;
        Map<BlockKind, Long> byKind = new LinkedHashMap<>();
        for (Block b : blocks) {
            long t = estimator.estimate(textOf(b));
            byKind.merge(b.kind(), t, Long::sum);
            used += t;
        }
        return new ContextMetrics(used, budgetTokens, Map.copyOf(byKind), Math.min(protectedTailBlocks, blocks.size()));
    }

    // ---- 内部 ----

    private static String textOf(Block b) {
        StringBuilder sb = new StringBuilder();
        if (b.text() != null) {
            sb.append(b.text());
        }
        if (b.thinking() != null) {
            sb.append(b.thinking());
        }
        for (ToolCall c : b.toolCalls()) {
            sb.append(c.name()).append(c.argumentsJson() == null ? "" : c.argumentsJson());
        }
        return sb.toString();
    }

    /**
     * 配对修复：
     * 1) 有调用无结果 → 补合成结果（模型协议要求每个 tool_call 必须有配对 tool 消息）；
     * 2) 有结果无调用 → 丢弃（孤儿结果对模型无意义）。
     */
    private static List<ChatMsg> repairPairing(List<ChatMsg> msgs) {
        Set<String> called = new HashSet<>();
        for (ChatMsg m : msgs) {
            for (ToolCall c : m.toolCalls()) {
                called.add(c.id());
            }
        }
        Set<String> answered = new HashSet<>();
        for (ChatMsg m : msgs) {
            if (m.role() == Role.TOOL) {
                answered.add(m.toolCallId());
            }
        }
        List<ChatMsg> out = new ArrayList<>(msgs.size());
        for (ChatMsg m : msgs) {
            if (m.role() == Role.TOOL && !called.contains(m.toolCallId())) {
                continue; // 孤儿结果丢弃
            }
            out.add(m);
            if (m.role() == Role.ASSISTANT) {
                for (ToolCall c : m.toolCalls()) {
                    if (!answered.contains(c.id())) {
                        out.add(ChatMsg.toolResult(c.id(), c.name(), "[工具执行结果缺失]", null));
                    }
                }
            }
        }
        return out;
    }
}
