package ai.csap.apidoc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.strategy.AsyncTaskUtil;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author ycf
 * @Date 2025/8/29 13:53
 * @Version 1.0
 */
@Slf4j
public class AsyncTest {

    @SneakyThrows
    public Integer thread1() {
        Thread.sleep(3000);
        log.info("thread1");
        return 1;
    }

    @SneakyThrows
    public Integer thread2() {
        Thread.sleep(2000);
        log.info("thread2");
        return 2;
    }

    @SneakyThrows
    public Integer thread3() {
        Thread.sleep(1000);
        log.info("thread3");
        return 3;
    }

    @Test
    @SneakyThrows
    public void asyncTest() {
        log.info("开始");
        List<Supplier<Integer>> list = new ArrayList<>();
        list.add(this::thread1);
        list.add(this::thread2);
        list.add(this::thread3);
        CompletableFuture<List<Integer>> listCompletableFuture = AsyncTaskUtil.submitAll(list);
        List<Integer> integers = listCompletableFuture.get();
        System.out.println(integers);
        log.info("结束");
    }
}
