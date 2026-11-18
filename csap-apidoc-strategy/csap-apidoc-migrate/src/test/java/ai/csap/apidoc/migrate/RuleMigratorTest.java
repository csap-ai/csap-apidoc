package ai.csap.apidoc.migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.database.DatabaseDialect;
import ai.csap.apidoc.database.DatabaseParamGroupStrategy;
import ai.csap.apidoc.database.DatabaseRuleStore;
import ai.csap.apidoc.database.autoconfigure.DatabaseAutoConfiguration;
import ai.csap.apidoc.migrate.autoconfigure.MigrateAutoConfiguration;
import ai.csap.apidoc.migrate.autoconfigure.RuleMigrationRunner;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.model.RuleChange;
import ai.csap.apidoc.strategy.SQLiteParamGroupStrategy;
import ai.csap.apidoc.strategy.store.RuleJson;
import ai.csap.validation.factory.Validate;

import cn.hutool.core.io.FileUtil;

class RuleMigratorTest {

    private static final String CLS = "com.example.UserController";
    private final String dir = "csap-migrate-test-" + System.nanoTime();

    @AfterEach
    void cleanUp() {
        FileUtil.del(new File(classpathRoot(), dir));
    }

    private static String classpathRoot() {
        return URLDecoder.decode(Thread.currentThread().getContextClassLoader().getResource("").getPath(), StandardCharsets.UTF_8);
    }

    private static ParamGroupMethodProperty property(String description, boolean required) {
        ParamGroupMethodProperty p = new ParamGroupMethodProperty();
        ParamGroupMethodProperty.ParamDataValidate v = new ParamGroupMethodProperty.ParamDataValidate();
        v.setRequired(required);
        v.setInclude(true);
        v.setParamType(ParamType.QUERY);
        v.setDescription(description);
        v.getValidate().add(new Validate.ConstraintValidatorField().setDescr("长度").setLevel(2).setMessage("太长").setPattern("^.{1,8}$"));
        p.getRequest().put("name", v);
        ParamGroupMethodProperty.ParamDataValidate r = new ParamGroupMethodProperty.ParamDataValidate();
        r.setDescription("响应 " + description);
        p.getResponse().put("data", r);
        return p;
    }

    private static Map<String, Map<String, ParamGroupMethodProperty>> sample() {
        Map<String, ParamGroupMethodProperty> methods = new HashMap<>();
        methods.put("add", property("姓名", true));
        methods.put("del", property("编号", false));
        methods.put("noop", new ParamGroupMethodProperty());
        Map<String, Map<String, ParamGroupMethodProperty>> all = new HashMap<>();
        all.put(CLS, methods);
        return all;
    }

    private static DataSource h2(String name) {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        return ds;
    }

    private static DatabaseRuleStore store(String name) throws Exception {
        DatabaseRuleStore s = new DatabaseRuleStore(h2(name), DatabaseDialect.H2, "mig_", "svc");
        s.init(true, false);
        return s;
    }

    // ------------------------------------------------------------------ 导入

    @Test
    void importReportsAndGoesThroughNormalSaveWithMigrateUser() throws Exception {
        DatabaseRuleStore target = store("mig_a" + System.nanoTime());
        RuleMigrator.Report r = RuleMigrator.importRules(sample(), target, false, false);
        assertEquals(List.of(CLS + ".add", CLS + ".del"), r.getImported());
        assertEquals(List.of(CLS + ".noop"), r.getEmpty());
        assertTrue(r.getConflicts().isEmpty());
        assertEquals(2L, target.version());
        List<RuleChange> history = target.history(null, 10);
        assertEquals(2, history.size());
        assertEquals("migrate", history.get(0).getUpdatedBy());
        // 修改记录里能回滚:回滚"新增" = 删除
        target.rollback(history.get(0).getId(), "ops");
        assertEquals(1, target.load().rules().size());
        // 再导入一次:已有的都算一致,被回滚删掉的重新新增
        RuleMigrator.Report again = RuleMigrator.importRules(sample(), target, false, false);
        assertEquals(1, again.getUnchanged().size());
        assertEquals(1, again.getImported().size());
    }

    @Test
    void conflictsAreReportedAndSkippedUnlessOverwrite() throws Exception {
        DatabaseRuleStore target = store("mig_b" + System.nanoTime());
        target.save(CLS + ".add", RuleJson.write(property("别人改过的", false)), "someone");
        RuleMigrator.Report r = RuleMigrator.importRules(sample(), target, false, false);
        assertEquals(List.of(CLS + ".add"), r.getConflicts());
        assertEquals(List.of(CLS + ".del"), r.getImported());
        assertTrue(r.hasConflicts());
        assertTrue(r.text().contains("冲突") && r.text().contains(CLS + ".add"), r.text());
        assertTrue(target.load().rules().get(CLS + ".add").contains("别人改过的"), "默认不覆盖");

        RuleMigrator.Report over = RuleMigrator.importRules(sample(), target, true, false);
        assertEquals(List.of(CLS + ".add"), over.getOverwritten());
        assertTrue(target.load().rules().get(CLS + ".add").contains("姓名"));
        assertEquals("migrate", target.history(CLS + ".add", 1).get(0).getUpdatedBy());
    }

    @Test
    void dryRunWritesNothing() throws Exception {
        DatabaseRuleStore target = store("mig_c" + System.nanoTime());
        RuleMigrator.Report r = RuleMigrator.importRules(sample(), target, false, true);
        assertTrue(r.isDryRun());
        assertEquals(2, r.getImported().size());
        assertEquals(0L, target.version());
        assertTrue(target.load().rules().isEmpty());
        assertTrue(r.text().contains("dry-run"));
        File out = RuleMigrator.exportYaml(sample(), new File(classpathRoot(), dir), true);
        assertFalse(out.exists());
    }

    // ------------------------------------------------------------------ 导出 YAML,再被 YAML 策略读回

    private EnableApidocConfig yamlConfig() {
        EnableApidocConfig cfg = new EnableApidocConfig();
        cfg.setPath(dir);
        cfg.setPrefixStrategy(FilePrefixStrategyType.DEFAULT);
        cfg.setType(ApiStrategyType.ANNOTATION);
        cfg.setParamType(ApiStrategyType.YAML);
        return cfg;
    }

    @Test
    void exportedYamlCanBeReadBackByTheYamlStrategy() throws Exception {
        DatabaseRuleStore source = store("mig_d" + System.nanoTime());
        RuleMigrator.importRules(sample(), source, false, false);
        File file = RuleMigrator.exportYaml(RuleMigrator.readStore(source), new File(classpathRoot(), dir), false);
        assertTrue(file.isFile(), file.getPath());
        assertTrue(file.getPath().endsWith("method-field" + File.separator + "api-method-field.yaml"));

        for (FilePrefixStrategyType mode : FilePrefixStrategyType.values()) {
            EnableApidocConfig cfg = yamlConfig();
            cfg.setPrefixStrategy(mode);
            Map<String, Map<String, ParamGroupMethodProperty>> back = RuleMigrator.readYaml(cfg);
            assertEquals(1, back.size(), mode.name());
            assertEquals(2, back.get(CLS).size(), mode.name());
            assertEquals(RuleJson.write(sample().get(CLS).get("add")), RuleJson.write(back.get(CLS).get("add")), mode.name());
            assertEquals(RuleJson.write(sample().get(CLS).get("del")), RuleJson.write(back.get(CLS).get("del")), mode.name());
        }

        // YAML → 另一个库,内容一致
        DatabaseRuleStore other = store("mig_e" + System.nanoTime());
        RuleMigrator.importRules(RuleMigrator.readYaml(yamlConfig()), other, false, false);
        assertEquals(source.load().rules().keySet(), other.load().rules().keySet());
        for (String k : source.load().rules().keySet()) {
            assertTrue(RuleJson.sameJson(source.load().rules().get(k), other.load().rules().get(k)), k);
        }
    }

    // ------------------------------------------------------------------ 启动时迁移

    @Configuration(proxyBeanMethods = false)
    static class Ds {
        @Bean
        DataSource dataSource() {
            return h2("mig_runner" + System.nanoTime());
        }
    }

    private ApplicationContextRunner runner(EnableApidocConfig cfg) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DatabaseAutoConfiguration.class, MigrateAutoConfiguration.class))
                .withUserConfiguration(Ds.class)
                .withBean(EnableApidocConfig.class, () -> cfg)
                .withPropertyValues("csap.apidoc.database.table-prefix=run_", "csap.apidoc.database.poll-interval=0",
                        "spring.application.name=mig");
    }

    @Test
    void runnerImportsYamlIntoTheDatabaseAndReloadsTheStrategy() throws Exception {
        DatabaseRuleStore tmp = store("mig_f" + System.nanoTime());
        RuleMigrator.importRules(sample(), tmp, false, false);
        RuleMigrator.exportYaml(RuleMigrator.readStore(tmp), new File(classpathRoot(), dir), false);

        runner(yamlConfig()).withPropertyValues("csap.apidoc.migrate.from=yaml", "csap.apidoc.migrate.to=database")
                            .run(ctx -> {
                                assertTrue(ctx.getStartupFailure() == null);
                                RuleMigrationRunner runner = ctx.getBean(RuleMigrationRunner.class);
                                runner.run(null);
                                assertEquals(2, runner.lastReport().getImported().size());
                                DatabaseParamGroupStrategy s = ctx.getBean(DatabaseParamGroupStrategy.class);
                                StrategyModel m = StrategyModel.builder().paramType(ApiStrategyType.DATABASE).build();
                                ai.csap.apidoc.model.CsapDocMethod dm = new ai.csap.apidoc.model.CsapDocMethod();
                                dm.setClassName(CLS);
                                dm.setName("add");
                                assertEquals("姓名", s.paramDataValidate(m, "name", dm, true).getDescription());
                                // 再跑一次:全部一致
                                runner.run(null);
                                assertEquals(2, runner.lastReport().getUnchanged().size());
                            });
    }

    @Test
    void runnerImportsSqliteIntoTheDatabase() throws Exception {
        SQLiteParamGroupStrategy sqlite = new SQLiteParamGroupStrategy(null);
        StrategyModel model = StrategyModel.builder().id("w-" + dir).path(dir).fileName(SQLiteHandle.DEFAULT_DB_NAME)
                                           .paramType(ApiStrategyType.SQL_LITE).build();
        for (Map.Entry<String, ParamGroupMethodProperty> e : sample().get(CLS).entrySet()) {
            if (e.getKey().equals("noop")) {
                continue;
            }
            Map<String, Map<String, ParamGroupMethodProperty>> data = new HashMap<>();
            data.computeIfAbsent(CLS, k -> new HashMap<>()).put(e.getKey(), e.getValue());
            // SQLite 策略按请求 / 响应分别保存
            sqlite.writeAndMerge(model, data, ApiStrategyType.SQL_LITE, CLS, e.getKey(), true);
            sqlite.writeAndMerge(model, data, ApiStrategyType.SQL_LITE, CLS, e.getKey(), false);
        }
        EnableApidocConfig cfg = yamlConfig();
        cfg.setParamType(ApiStrategyType.SQL_LITE);
        cfg.setFileName(SQLiteHandle.DEFAULT_DB_NAME);
        runner(cfg).withPropertyValues("csap.apidoc.migrate.from=sqlite", "csap.apidoc.migrate.to=database")
                   .run(ctx -> {
                       RuleMigrationRunner runner = ctx.getBean(RuleMigrationRunner.class);
                       runner.run(null);
                       assertEquals(2, runner.lastReport().getImported().size(), runner.lastReport().text());
                       DatabaseParamGroupStrategy s = ctx.getBean(DatabaseParamGroupStrategy.class);
                       for (String method : new String[]{"add", "del"}) {
                           String json = s.store().load().rules().get(CLS + "." + method);
                           assertTrue(RuleJson.sameJson(json, RuleJson.write(sample().get(CLS).get(method))), method + " " + json);
                       }
                   });
    }

    @Test
    void runnerExportsTheDatabaseToAYamlDirectory() throws Exception {
        File out = new File(classpathRoot(), dir + "/exported");
        runner(yamlConfig()).withPropertyValues("csap.apidoc.migrate.from=database",
                                    "csap.apidoc.migrate.export-to=" + out.getPath())
                            .run(ctx -> {
                                DatabaseParamGroupStrategy s = ctx.getBean(DatabaseParamGroupStrategy.class);
                                s.store().save(CLS + ".add", RuleJson.write(sample().get(CLS).get("add")), "x");
                                s.reload();
                                ctx.getBean(RuleMigrationRunner.class).run(null);
                                assertTrue(new File(out, RuleMigrator.EXPORT_FILE).isFile());
                            });
    }

    @Test
    void runnerRejectsBadArguments() {
        runner(yamlConfig()).withPropertyValues("csap.apidoc.migrate.from=yaml").run(ctx -> {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> ctx.getBean(RuleMigrationRunner.class).run(null));
            assertTrue(e.getMessage().contains("目标"), e.getMessage());
        });
        runner(yamlConfig()).withPropertyValues("csap.apidoc.migrate.from=oracle", "csap.apidoc.migrate.to=database").run(ctx -> {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> ctx.getBean(RuleMigrationRunner.class).run(null));
            assertTrue(e.getMessage().contains("不支持"), e.getMessage());
        });
        runner(yamlConfig()).withPropertyValues("csap.apidoc.migrate.from=yaml", "csap.apidoc.migrate.export-to=/tmp/x").run(ctx -> {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> ctx.getBean(RuleMigrationRunner.class).run(null));
            assertTrue(e.getMessage().contains("database 或 redis"), e.getMessage());
        });
        runner(yamlConfig()).withPropertyValues("csap.apidoc.migrate.from=yaml", "csap.apidoc.migrate.to=database",
                "csap.apidoc.database.read-only=true").run(ctx -> {
            assertThrows(IllegalStateException.class, () -> ctx.getBean(RuleMigrationRunner.class).run(null));
        });
        // 没设置 from 时不注册 Runner
        runner(yamlConfig()).run(ctx -> assertFalse(ctx.containsBean("ruleMigrationRunner")));
    }
}
