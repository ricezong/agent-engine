package cn.kong.engine.scope;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import cn.kong.engine.EngineException;
import cn.kong.engine.loop.RunResult;

/** 一次 run 的句柄：等待结果。被拒绝的 run 也返回已完成句柄（REJECTED_BUSY）。 */
public final class RunHandle {

    private final Future<RunResult> future;

    public RunHandle(Future<RunResult> future) {
        this.future = future;
    }

    public static RunHandle completed(RunResult result) {
        return new RunHandle(CompletableFuture.completedFuture(result));
    }

    public RunResult await() {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException("等待运行结果时被中断", e);
        } catch (ExecutionException e) {
            throw new EngineException("运行内部异常", e.getCause());
        }
    }

    public RunResult await(Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new EngineException("等待运行结果超时: " + timeout, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineException("等待运行结果时被中断", e);
        } catch (ExecutionException e) {
            throw new EngineException("运行内部异常", e.getCause());
        }
    }
}
