package cn.kong.engine.port.context;

/** token 估算端口。粗估即可用于水位判断；精确实现按模型提供。 */
@FunctionalInterface
public interface TokenEstimator {

    long estimate(String text);
}
