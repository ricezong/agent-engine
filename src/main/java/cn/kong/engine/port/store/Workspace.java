package cn.kong.engine.port.store;

import java.nio.file.Path;

/** 文件工作区：工具文件操作的沙箱根。resolve 必须把路径限制在根目录内。 */
public interface Workspace {

    Path root();

    /** 解析相对路径；越界路径抛 IllegalArgumentException。 */
    Path resolve(String relativePath);
}
