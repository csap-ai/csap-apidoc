package ai.csap.apidoc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.strategy.ApidocStrategy;

/**
 * {@link ApidocContext#cmd(Boolean, Boolean)}:flush=true 必须重新扫描;扫描未完成时不能对外暴露半成品。
 */
class ApidocContextCmdTest {

    private final AtomicInteger scans = new AtomicInteger();
    private volatile CountDownLatch scanStarted;
    private volatile CountDownLatch releaseScan;

    @BeforeEach
    void setUp() throws Exception {
        // 清掉其他用例留下的静态缓存
        java.lang.reflect.Field f = ApidocContext.class.getDeclaredField("csapDocResponse");
        f.setAccessible(true);
        f.set(null, null);
        scanStarted = null;
        releaseScan = null;

        ApidocStrategy strategy = mock(ApidocStrategy.class);
        when(strategy.getName()).thenReturn(ApiStrategyType.ANNOTATION.getName());
        when(strategy.apidoc(isNull(), any(), any(StrategyModel.class), any(CsapDocResponse.class))).thenAnswer(inv -> {
            CsapDocResponse response = inv.getArgument(3);
            int n = scans.incrementAndGet();
            CountDownLatch started = scanStarted;
            CountDownLatch release = releaseScan;
            // 先写入一半
            CsapDocModelController a = new CsapDocModelController();
            a.setName("A" + n);
            a.setMethodList(new ArrayList<>());
            response.getApiList().add(a);
            if (started != null) {
                started.countDown();
                release.await(10, TimeUnit.SECONDS);
            }
            CsapDocModelController b = new CsapDocModelController();
            b.setName("B" + n);
            b.setMethodList(new ArrayList<>());
            response.getApiList().add(b);
            return response;
        });

        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansOfType(ApidocStrategy.class)).thenReturn(Map.of("annotation", strategy));
        when(ctx.getBeansOfType(eq(ai.csap.apidoc.strategy.ParamGroupStrategy.class))).thenReturn(Collections.emptyMap());
        when(ctx.getBeansOfType(eq(IMethodRequest.class))).thenReturn(Collections.emptyMap());
        when(ctx.getBeansOfType(eq(IMethodResponse.class))).thenReturn(Collections.emptyMap());

        CsapDocConfig docConfig = new CsapDocConfig();
        ScannerPackageConfig scannerConfig = new ScannerPackageConfig(docConfig);
        scannerConfig.addApiClassesList(List.of(StrategyModel.builder()
                .docType(ApiStrategyType.ANNOTATION)
                .clazz(new HashSet<>())
                .build()));
        ApidocContext context = new ApidocContext(null, scannerConfig, docConfig);
        context.setApplicationContext(ctx);
        context.afterSingletonsInstantiated();
    }

    @Test
    void cachedWithoutFlushAndRescannedWithFlush() {
        CsapDocResponse first = ApidocContext.cmd(false, false);
        assertEquals(1, scans.get());
        assertSame(first, ApidocContext.cmd(false, false));
        assertEquals(1, scans.get());

        CsapDocResponse flushed = ApidocContext.cmd(true, false);
        assertEquals(2, scans.get(), "flush=true 必须重新扫描");
        assertNotSame(first, flushed);
        assertEquals(2, flushed.getApiList().size());
        assertSame(flushed, ApidocContext.cmd(false, false));
    }

    @Test
    void requestDuringScanWaitsForCompleteDocument() throws Exception {
        scanStarted = new CountDownLatch(1);
        releaseScan = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // 模拟启动时的异步扫描
            Future<CsapDocResponse> startup = pool.submit(() -> ApidocContext.cmd(true, false));
            assertTrue(scanStarted.await(10, TimeUnit.SECONDS));
            // 扫描进行到一半时来了一个普通请求
            Future<CsapDocResponse> request = pool.submit(() -> ApidocContext.cmd(false, false));
            Thread.sleep(200);
            assertTrue(!request.isDone(), "扫描未完成时请求应等待,而不是拿到半成品");
            releaseScan.countDown();

            CsapDocResponse doc = request.get(10, TimeUnit.SECONDS);
            assertSame(startup.get(10, TimeUnit.SECONDS), doc);
            assertEquals(2, doc.getApiList().size(), "拿到的必须是完整文档");
            assertEquals(1, scans.get(), "等待者复用启动扫描的结果,不重复扫描");
        } finally {
            releaseScan.countDown();
            pool.shutdownNow();
        }
    }
}
