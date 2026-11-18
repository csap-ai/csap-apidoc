package ai.csap.apidoc.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocResultEnum;
import ai.csap.apidoc.handle.IStandardHandle;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.util.RuleStorageNotWritableException;

import cn.hutool.core.io.FileUtil;

/**
 * 以打包后的 jar 运行(getResource("") 为 null)时,SQLite 规则保存在启动目录(user.dir)下的文件,重启后可读回;
 * 目录不可写时返回说明清楚的错误。
 */
class SQLiteJarModeTest {

    private static final String CLASS_NAME = "com.example.UserController";

    private ClassLoader originalLoader;
    private String originalUserDir;
    private Path workDir;

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

    @BeforeEach
    void setUp() throws Exception {
        originalLoader = Thread.currentThread().getContextClassLoader();
        originalUserDir = System.getProperty("user.dir");
        workDir = Files.createTempDirectory("csap-sqlite-jar-");
        System.setProperty("user.dir", workDir.toString());
        Thread.currentThread().setContextClassLoader(new JarLikeClassLoader(originalLoader));
    }

    @AfterEach
    void tearDown() {
        Thread.currentThread().setContextClassLoader(originalLoader);
        System.setProperty("user.dir", originalUserDir);
        workDir.toFile().setWritable(true);
        FileUtil.del(workDir.toFile());
    }

    private static StrategyModel model(String id) {
        return StrategyModel.builder()
                .id(id)
                .path("csap-api-doc")
                .fileName(SQLiteHandle.DEFAULT_DB_NAME)
                .paramType(ApiStrategyType.SQL_LITE)
                .build();
    }

    private static Map<String, Map<String, ParamGroupMethodProperty>> data() {
        Map<String, ParamGroupMethodProperty.ParamDataValidate> response = new HashMap<>();
        response.put("id", ParamGroupMethodProperty.ParamDataValidate.builder().include(true).build());
        ParamGroupMethodProperty property = new ParamGroupMethodProperty();
        property.setResponse(response);
        Map<String, Map<String, ParamGroupMethodProperty>> data = new HashMap<>();
        data.computeIfAbsent(CLASS_NAME, k -> new HashMap<>()).put("getUser", property);
        return data;
    }

    @Test
    void savesOutsideTheJarAndReadsBackAfterRestart() {
        SQLiteParamGroupStrategy strategy = new SQLiteParamGroupStrategy(null);
        String id = "jar-" + System.nanoTime();
        // 还没有任何规则文件:返回空规则而不是 null(以前 DevTools 保存时 NPE)
        Map<String, Map<String, ParamGroupMethodProperty>> empty = strategy.handle(model(id), ApiStrategyType.SQL_LITE, null, null);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());

        strategy.writeAndMerge(model(id), data(), ApiStrategyType.SQL_LITE, CLASS_NAME, "getUser", false);
        File db = workDir.resolve("csap-api-doc").resolve("apidoc.db").toFile();
        assertTrue(db.isFile(), "以 jar 运行时应写到启动目录下的 csap-api-doc/apidoc.db");

        // 模拟重启:新的 id(静态缓存里没有),从 jar 外的文件读回
        Map<String, Map<String, ParamGroupMethodProperty>> reloaded = new SQLiteParamGroupStrategy(null)
                .handle(model("restart-" + System.nanoTime()), ApiStrategyType.SQL_LITE, null, null);
        assertTrue(reloaded.containsKey(CLASS_NAME), String.valueOf(reloaded));
        assertTrue(reloaded.get(CLASS_NAME).get("getUser").getResponse().containsKey("id"));
    }

    @Test
    void unwritableWorkingDirectoryReturnsClearError() {
        assertTrue(workDir.toFile().setWritable(false));
        SQLiteParamGroupStrategy strategy = new SQLiteParamGroupStrategy(null);
        RuleStorageNotWritableException e = assertThrows(RuleStorageNotWritableException.class,
                () -> strategy.writeAndMerge(model("ro-" + System.nanoTime()), data(), ApiStrategyType.SQL_LITE,
                        CLASS_NAME, "getUser", false));
        assertEquals(ApidocResultEnum.RULE_STORAGE_NOT_WRITABLE.getCode(), e.getCode());
        assertTrue(e.getMessage().contains("apidoc.db") && e.getMessage().contains("请确认该目录可写"), e.getMessage());
    }
}
