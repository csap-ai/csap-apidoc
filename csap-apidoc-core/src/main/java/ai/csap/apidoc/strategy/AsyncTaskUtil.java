package ai.csap.apidoc.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * 自定义CompletableFuture工具类，简化多线程任务提交与结果处理
 *
 * @Author ycf
 * @Date 2025/8/29 13:58
 * @Version 1.0
 */
@Slf4j
public class AsyncTaskUtil {

    // 自定义线程池（核心线程数=CPU核心数，最大线程数=2*CPU核心数，避免资源耗尽）
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(4096),
            new ThreadFactory() {
                private int count = 0;

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("async-task-" + (++count));
                    thread.setDaemon(false); // 非守护线程，避免主线程退出导致任务中断
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，让提交任务的线程执行，避免任务丢失
    );

    /**
     * 提交无返回值的异步任务（不捕获异常，让异常向上传播）
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> submit(Runnable task) {
        return CompletableFuture.runAsync(task, EXECUTOR);
    }

    /**
     * 提交无返回值的异步任务（捕获异常并记录日志，不向上传播）
     * 适用于不希望单个任务失败影响整体流程的场景
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> submitWithExceptionHandling(Runnable task) {
        return CompletableFuture.runAsync(task, EXECUTOR)
                .exceptionally(ex -> {
                    handleException(ex);
                    return null;
                });
    }

    /**
     * 提交有返回值的异步任务（不捕获异常，让异常向上传播）
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> submit(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, EXECUTOR);
    }

    /**
     * 提交有返回值的异步任务（捕获异常并记录日志，返回null）
     * 适用于不希望单个任务失败影响整体流程的场景
     *
     * @param task 任务
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> submitWithExceptionHandling(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, EXECUTOR)
                .exceptionally(ex -> {
                    handleException(ex);
                    return null;
                });
    }

    /**
     * 提交多个有返回值的任务，并聚合结果
     *
     * @param tasks 任务列表
     * @return 结果列表的CompletableFuture
     */
    public static <T> CompletableFuture<List<T>> submitAll(List<Supplier<T>> tasks) {
        // 提交所有任务
        List<CompletableFuture<T>> futures = tasks.stream()
                .map(AsyncTaskUtil::submit)
                .collect(Collectors.toList());

        // 聚合所有结果
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * 处理任务结果（非阻塞）
     *
     * @param future   任务Future
     * @param consumer 结果处理器
     */
    public static <T> void handleResult(CompletableFuture<T> future, Consumer<T> consumer) {
        future.thenAccept(consumer);
    }

    /**
     * 处理任务结果并转换（非阻塞）
     *
     * @param future   任务Future
     * @param function 结果转换函数
     * @return 转换后的结果Future
     */
    public static <T, R> CompletableFuture<R> handleAndTransform(CompletableFuture<T> future, Function<T, R> function) {
        return future.thenApply(function);
    }

    /**
     * 等待所有任务完成（阻塞）
     * 如果任何任务失败，会抛出CompletionException，包含第一个失败任务的异常
     * 会收集所有失败任务的异常信息并记录日志
     *
     * @param futures 任务Future列表
     * @throws CompletionException 如果有任务执行失败
     */
    public static void waitAll(List<? extends CompletableFuture<?>> futures) {
        if (futures == null || futures.isEmpty()) {
            return;
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            // 收集所有失败的任务异常
            List<Throwable> exceptions = new ArrayList<>();
            for (CompletableFuture<?> future : futures) {
                if (future.isCompletedExceptionally()) {
                    try {
                        future.join();
                    } catch (CompletionException ex) {
                        exceptions.add(ex.getCause() != null ? ex.getCause() : ex);
                    }
                }
            }

            // 记录所有异常
            if (!exceptions.isEmpty()) {
                log.error("异步任务执行失败，共 {} 个任务失败:", exceptions.size());
                for (int i = 0; i < exceptions.size(); i++) {
                    log.error("失败任务 #{}: {}", i + 1, exceptions.get(i).getMessage(), exceptions.get(i));
                }
            }

            // 重新抛出第一个异常
            throw e;
        }
    }

    /**
     * 等待所有任务完成（阻塞），忽略异常
     * 即使某些任务失败，也会等待所有任务完成，并记录失败信息
     * 适用于希望所有任务都执行完毕，但不希望因个别失败而中断的场景
     *
     * @param futures 任务Future列表
     * @return 失败的任务数量
     */
    public static int waitAllIgnoreException(List<? extends CompletableFuture<?>> futures) {
        if (futures == null || futures.isEmpty()) {
            return 0;
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return 0;
        } catch (CompletionException e) {
            // 收集所有失败的任务
            List<Throwable> exceptions = new ArrayList<>();
            for (CompletableFuture<?> future : futures) {
                if (future.isCompletedExceptionally()) {
                    try {
                        future.join();
                    } catch (CompletionException ex) {
                        exceptions.add(ex.getCause() != null ? ex.getCause() : ex);
                    }
                }
            }

            // 记录所有异常
            if (!exceptions.isEmpty()) {
                log.warn("异步任务部分失败，共 {} 个任务失败（已忽略）:", exceptions.size());
                for (int i = 0; i < exceptions.size(); i++) {
                    log.warn("失败任务 #{}: {}", i + 1, exceptions.get(i).getMessage(), exceptions.get(i));
                }
            }

            return exceptions.size();
        }
    }

    /**
     * 获取第一个完成的任务结果（阻塞）
     *
     * @param futures 任务Future列表
     * @return 第一个完成的结果
     */
    public static <T> T waitAny(List<CompletableFuture<T>> futures) {
        try {
            return (T) CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).get();
        } catch (InterruptedException | ExecutionException e) {
            handleException(e);
            return null;
        }
    }

    /**
     * 关闭线程池（应用退出时调用）
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
        }
    }

    /**
     * 统一异常处理
     */
    private static void handleException(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        log.error("异步任务执行异常: {}", cause.getMessage(), cause);
        // 可根据需要扩展：报警通知等
    }
}
