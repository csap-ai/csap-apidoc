package ai.csap.apidoc.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.boot.autoconfigure.dbe2e.DbE2eController;
import ai.csap.apidoc.database.autoconfigure.DatabaseAutoConfiguration;
import ai.csap.apidoc.migrate.RuleMigrator;
import ai.csap.apidoc.migrate.autoconfigure.MigrateAutoConfiguration;
import ai.csap.apidoc.migrate.autoconfigure.RuleMigrationRunner;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.redis.autoconfigure.RedisRuleAutoConfiguration;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.strategy.SQLiteParamGroupStrategy;
import ai.csap.apidoc.strategy.store.RuleStoreParamGroupStrategy;
import ai.csap.validation.factory.Validate;

import cn.hutool.core.io.FileUtil;

/**
 * 迁移后文档输出与迁移前逐字节一致(docs/design/database-mode.md 第 4 节):
 * YAML / SQLite → 数据库 / Redis,以及数据库 → 导出 YAML 再由 YAML 策略读回。
 */
class RuleMigrationDocumentTest {

    private static final String PKG = "ai.csap.apidoc.boot.autoconfigure.dbe2e";
    private static final String CTRL = DbE2eController.class.getName();
    /**
     * 注解里只能写常量:规则目录都放在 test-classes 下
     */
    static final String SRC_DIR = "csap-api-doc-migration-src";
    static final String EXPORT_DIR = "csap-api-doc-migration-export";
    private static final ObjectMapper MAPPER = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    @Configuration
    @EnableWebMvc
    @Import(DbE2eController.class)
    @EnableApidoc(value = PKG, path = SRC_DIR, fileName = "apidoc-migration", paramType = ApiStrategyType.YAML)
    static class YamlApp {
    }

    @Configuration
    @EnableWebMvc
    @Import(DbE2eController.class)
    @EnableApidoc(value = PKG, path = SRC_DIR, fileName = "apidoc-migration", paramType = ApiStrategyType.SQL_LITE)
    static class SqliteApp {
    }

    @Configuration
    @EnableWebMvc
    @Import(DbE2eController.class)
    @EnableApidoc(value = PKG, path = SRC_DIR, fileName = "apidoc-migration", paramType = ApiStrategyType.DATABASE)
    static class DatabaseApp {
        @Bean
        DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:migration_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            return ds;
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(DbE2eController.class)
    @EnableApidoc(value = PKG, path = SRC_DIR, fileName = "apidoc-migration", paramType = ApiStrategyType.REDIS)
    static class RedisApp {
    }

    @Configuration
    @EnableWebMvc
    @Import(DbE2eController.class)
    @EnableApidoc(value = PKG, path = EXPORT_DIR, paramType = ApiStrategyType.YAML)
    static class ExportedYamlApp {
    }

    private static String classpathRoot() {
        return URLDecoder.decode(Thread.currentThread().getContextClassLoader().getResource("").getPath(), StandardCharsets.UTF_8);
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        FileUtil.del(new File(classpathRoot(), SRC_DIR));
        FileUtil.del(new File(classpathRoot(), EXPORT_DIR));
    }

    // ------------------------------------------------------------------ 固定的规则

    private static ParamGroupMethodProperty.ParamDataValidate field(String description, boolean required, ParamType type) {
        ParamGroupMethodProperty.ParamDataValidate v = new ParamGroupMethodProperty.ParamDataValidate();
        v.setRequired(required);
        v.setInclude(true);
        v.setParamType(type);
        v.setDescription(description);
        if (required) {
            v.getValidate().add(new Validate.ConstraintValidatorField().setDescr("必填").setLevel(1).setMessage(description + "不能为空"));
        }
        return v;
    }

    static Map<String, Map<String, ParamGroupMethodProperty>> rules() {
        ParamGroupMethodProperty query = new ParamGroupMethodProperty();
        query.getRequest().put("keyword.keyword", field("关键字", true, ParamType.QUERY));
        ParamGroupMethodProperty save = new ParamGroupMethodProperty();
        save.getRequest().put("req.name", field("名称", true, ParamType.BODY));
        save.getRequest().put("req.age", field("年龄", false, ParamType.BODY));
        save.getResponse().put("id", field("编号", false, null));
        save.getResponse().put("name", field("返回的名称", false, null));
        Map<String, ParamGroupMethodProperty> methods = new HashMap<>();
        methods.put("query", query);
        methods.put("save", save);
        Map<String, Map<String, ParamGroupMethodProperty>> all = new HashMap<>();
        all.put(CTRL, methods);
        return all;
    }

    private static void writeYamlRules(String dir) throws Exception {
        RuleMigrator.exportYaml(rules(), new File(classpathRoot(), dir), false);
    }

    private static void writeSqliteRules() {
        SQLiteParamGroupStrategy sqlite = new SQLiteParamGroupStrategy(null);
        StrategyModel model = StrategyModel.builder().id("w-" + System.nanoTime()).path(SRC_DIR)
                                           .fileName("apidoc-migration").paramType(ApiStrategyType.SQL_LITE).build();
        rules().get(CTRL).forEach((method, property) -> {
            Map<String, Map<String, ParamGroupMethodProperty>> data = new HashMap<>();
            data.computeIfAbsent(CTRL, k -> new HashMap<>()).put(method, property);
            sqlite.writeAndMerge(model, data, ApiStrategyType.SQL_LITE, CTRL, method, true);
            sqlite.writeAndMerge(model, data, ApiStrategyType.SQL_LITE, CTRL, method, false);
        });
    }

    // ------------------------------------------------------------------ 工具

    private static WebApplicationContextRunner runner(Class<?> app, String... props) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApidocAutoConfiguration.class, DatabaseAutoConfiguration.class,
                        RedisRuleAutoConfiguration.class, MigrateAutoConfiguration.class))
                .withUserConfiguration(app)
                .withPropertyValues("csap.apidoc.database.poll-interval=0", "csap.apidoc.redis.poll-interval=0",
                        "csap.apidoc.redis.subscribe=false", "spring.application.name=migration-e2e")
                .withPropertyValues(props);
    }

    private static String document() throws Exception {
        CsapDocResponse doc = ApidocContext.cmd(true, false);
        // 每次扫描都会给字段生成一个新的随机 key(十六进制串),与规则来源无关,比较前统一抹平;其余逐字节比较
        return MAPPER.writeValueAsString(doc).replaceAll("\"key\":\"[0-9a-f]+\"", "\"key\":\"<generated>\"");
    }

    /**
     * 跑完 body 后取一份文档
     */
    private static String documentOf(WebApplicationContextRunner runner) {
        String[] out = new String[1];
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            out[0] = document();
        });
        return out[0];
    }

    private static void migrate(WebApplicationContextRunner runner, Consumer<ConfigurableApplicationContext> after,
                                String[] doc) {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            ctx.getBean(RuleMigrationRunner.class).run(null);
            assertThat(ctx.getBean(RuleMigrationRunner.class).lastReport().getImported()).hasSize(2);
            doc[0] = document();
            if (after != null) {
                after.accept(ctx.getSourceApplicationContext() instanceof ConfigurableApplicationContext
                        ? (ConfigurableApplicationContext) ctx.getSourceApplicationContext() : null);
            }
        });
    }

    private static void assertRulesAreInTheDocument(String doc) {
        assertThat(doc).contains("关键字").contains("名称").contains("年龄").contains("名称不能为空");
    }

    // ------------------------------------------------------------------ 测试

    @Test
    void yamlToDatabaseKeepsTheDocumentByteForByte() throws Exception {
        writeYamlRules(SRC_DIR);
        String before = documentOf(runner(YamlApp.class));
        assertRulesAreInTheDocument(before);
        String again = documentOf(runner(YamlApp.class));
        assertThat(again).as("文档生成本身是确定的").isEqualTo(before);

        String[] after = new String[1];
        migrate(runner(DatabaseApp.class, "csap.apidoc.migrate.from=yaml", "csap.apidoc.migrate.to=database"), null, after);
        assertThat(after[0].getBytes(StandardCharsets.UTF_8)).isEqualTo(before.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void sqliteToDatabaseKeepsTheDocumentByteForByte() throws Exception {
        writeSqliteRules();
        String before = documentOf(runner(SqliteApp.class));
        assertRulesAreInTheDocument(before);

        String[] after = new String[1];
        migrate(runner(DatabaseApp.class, "csap.apidoc.migrate.from=sql_lite", "csap.apidoc.migrate.to=database"), null, after);
        assertThat(after[0].getBytes(StandardCharsets.UTF_8)).isEqualTo(before.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void databaseExportedToYamlIsReadBackWithTheSameDocument() throws Exception {
        writeYamlRules(SRC_DIR);
        String before = documentOf(runner(YamlApp.class));
        File export = new File(classpathRoot(), EXPORT_DIR);

        // 导入数据库后导出成 YAML 目录
        runner(DatabaseApp.class, "csap.apidoc.migrate.from=yaml", "csap.apidoc.migrate.to=database").run(ctx -> {
            ctx.getBean(RuleMigrationRunner.class).run(null);
            RuleMigrator.exportYaml(RuleMigrator.readStore(ctx.getBean(
                    ai.csap.apidoc.database.DatabaseParamGroupStrategy.class).store()), export, false);
        });
        assertThat(new File(export, RuleMigrator.EXPORT_FILE)).isFile();
        // YAML 策略读回导出的目录:文档与最初一致
        assertThat(documentOf(runner(ExportedYamlApp.class))).isEqualTo(before);
    }

    @Test
    void redisKeepsTheDocumentByteForByte() throws Exception {
        boolean docker;
        try {
            docker = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable e) {
            docker = false;
        }
        Assumptions.assumeTrue(docker, "没有 Docker,跳过 Redis 迁移测试");
        try (GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379)) {
            redis.start();
            LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            factory.afterPropertiesSet();
            try {
                writeYamlRules(SRC_DIR);
                String before = documentOf(runner(YamlApp.class));

                String[] after = new String[1];
                WebApplicationContextRunner r = runner(RedisApp.class, "csap.apidoc.migrate.from=yaml", "csap.apidoc.migrate.to=redis")
                        .withBean(StringRedisTemplate.class, () -> {
                            StringRedisTemplate t = new StringRedisTemplate(factory);
                            t.afterPropertiesSet();
                            return t;
                        });
                migrate(r, null, after);
                assertThat(after[0].getBytes(StandardCharsets.UTF_8)).isEqualTo(before.getBytes(StandardCharsets.UTF_8));

                // 数据库 → redis 也可以;redis → YAML 导出再读回
                File export = new File(classpathRoot(), EXPORT_DIR);
                r.withPropertyValues("csap.apidoc.migrate.from=redis", "csap.apidoc.migrate.to=",
                        "csap.apidoc.migrate.export-to=" + export.getPath()).run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    ctx.getBean(RuleMigrationRunner.class).run(null);
                });
                assertThat(documentOf(runner(ExportedYamlApp.class))).isEqualTo(before);
            } finally {
                factory.destroy();
            }
        }
    }
}
