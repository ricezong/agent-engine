package cn.kong.engine.hook;

/**
 * 钩子标记接口：横切能力（预算/压缩/门禁/熔断）的统一挂载形式。
 * order 决定同阶段执行顺序（小者先）。
 */
public interface Hook {

    default String name() {
        return getClass().getSimpleName();
    }

    default int order() {
        return 100;
    }
}
