package cn.kong.engine.port.compression;

import java.util.List;

import cn.kong.engine.msg.ChatMsg;

/** 摘要端口：压缩策略 SUMMARIZE 档位使用。 */
public interface SummarizerPort {

    String summarize(List<ChatMsg> history, int maxOutputChars);
}
