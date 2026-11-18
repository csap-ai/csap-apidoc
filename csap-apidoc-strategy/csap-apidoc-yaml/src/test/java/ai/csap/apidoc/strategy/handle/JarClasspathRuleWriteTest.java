package ai.csap.apidoc.strategy.handle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.core.ApidocResultEnum;
import ai.csap.apidoc.handle.IStandardHandle;
import ai.csap.apidoc.properties.CsapApiInfo;
import ai.csap.apidoc.util.RuleStorageNotWritableException;

/**
 * 以打包后的 jar 运行时 {@code getResource("")} 为 null:读取不应 NPE,写入应返回说明清楚的错误而不是 NPE。
 */
class JarClasspathRuleWriteTest {

    private static final String PATH = "csap-api-doc-jar-test";

    /**
     * 模拟 Spring Boot fat jar 的类加载器:classpath 根不是目录,getResource("") 返回 null
     */
    static final class JarLikeClassLoader extends ClassLoader {
        JarLikeClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            return name.isEmpty() ? null : super.getResource(name);
        }
    }

    /**
     * 先用正常的类加载器初始化 IStandardHandle 的静态 RESOURCE_PATTERN(它在初始化时记下当前线程上下文类加载器),
     * 避免被下面模拟的 jar 类加载器“污染”,影响同一 JVM 中后续的测试
     */
    @BeforeAll
    static void initResolverWithNormalClassLoader() {
        assertNotNull(IStandardHandle.RESOURCE_PATTERN);
    }

    private static <T> T inJar(Supplier<T> action) {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        thread.setContextClassLoader(new JarLikeClassLoader(original));
        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void readingWithoutRuleFilesDoesNotThrow() {
        InfoHandle handle = new InfoHandle(new EnableApidocConfig());
        CsapApiInfo info = inJar(() -> handle.handle(ApiStrategyType.YAML, PATH, FilePrefixStrategyType.FIXED.getName()));
        assertNotNull(info);
        assertTrue(inJar(() -> !handle.isLocal()));
    }

    @Test
    void writingYamlInJarReturnsClearError() {
        RuleStorageNotWritableException e = writeInJar(ApiStrategyType.YAML);
        assertEquals(ApidocResultEnum.RULE_STORAGE_NOT_WRITABLE.getCode(), e.getCode());
        String msg = e.getMessage();
        assertTrue(msg.contains("jar"), msg);
        assertTrue(msg.contains("YAML 规则文件位于 jar 内"), msg);
        // 说清楚打包运行时哪些方式可以保存
        assertTrue(msg.contains("在源码目录运行"), msg);
        assertTrue(msg.contains("SQL_LITE") && msg.contains("./csap-api-doc/apidoc.db"), msg);
        assertTrue(msg.contains("1.4.0") && msg.contains("MySQL / PostgreSQL / 达梦 / 人大金仓"), msg);
        assertTrue(msg.contains("当前版本尚未提供"), msg);
        assertTrue(msg.contains("Redis 模式"), msg);
        assertTrue(msg.contains("Running from a packaged jar"), msg);
    }

    @Test
    void writingJsonInJarNamesTheStrategy() {
        RuleStorageNotWritableException e = writeInJar(ApiStrategyType.JSON);
        assertTrue(e.getMessage().contains("JSON 规则文件位于 jar 内"), e.getMessage());
    }

    private static RuleStorageNotWritableException writeInJar(ApiStrategyType type) {
        InfoHandle handle = new InfoHandle(new EnableApidocConfig());
        CsapApiInfo info = new CsapApiInfo();
        info.setTitle("jar");
        return inJar(() -> assertThrows(RuleStorageNotWritableException.class,
                () -> handle.write(info, type, PATH, FilePrefixStrategyType.FIXED.getName())));
    }
}
