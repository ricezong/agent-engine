package cn.kong.engine.port.store;

import java.nio.file.Path;

/**
 * 文件工作区端口：工具文件操作的读写分区契约。
 *
 * <p>会话归属是端口契约的一部分：实现按 sessionId 路由到该会话的目录，
 * 实现单例即可，无需按会话构造。
 *
 * <p>读写分区（会话目录内的两类边界）：
 * <ul>
 *   <li><b>读区</b>＝会话目录整体（sessionRoot 为上限）：resolve 把读路径
 *       限制在会话目录内，读类工具由此覆盖 upload / skills / tool-results 等只读子区</li>
 *   <li><b>写区</b>＝实现约定的可写子区（如 download 与 scripts）：
 *       resolveWritable 把写路径限制在可写子区内，写类工具一律走它，无法绕过</li>
 * </ul>
 *
 * <p>相对路径的解析基准（通常是 download 工作区子目录）是实现细节，
 * 不在端口上暴露；relativize 是 resolve 的逆运算，统一工具回传路径口径，
 * 保证模型、前端与工具三方对「相对路径」的定义一致。
 */
public interface Workspace {

    /** 指定会话的会话目录根（读沙箱上限）。 */
    Path sessionRoot(String sessionId);

    /** 解析读路径到指定会话的目录边界内；越界路径抛 IllegalArgumentException。 */
    Path resolve(String sessionId, String relativePath);

    /** 解析写路径到指定会话的可写子区内；越界路径抛 IllegalArgumentException。 */
    Path resolveWritable(String sessionId, String relativePath);

    /** 绝对路径还原为相对路径口径（正斜杠；resolve 的逆），会话目录外退化为文件名。 */
    String relativize(String sessionId, Path absolute);
}
