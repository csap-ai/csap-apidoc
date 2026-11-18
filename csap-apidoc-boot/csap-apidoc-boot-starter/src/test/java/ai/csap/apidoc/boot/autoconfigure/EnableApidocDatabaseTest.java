package ai.csap.apidoc.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.database.DatabaseParamGroupStrategy;
import ai.csap.apidoc.database.DatabaseRuleStore;
import ai.csap.apidoc.database.autoconfigure.DatabaseAutoConfiguration;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.boot.autoconfigure.dbe2e.DbE2eController;

/**
 * {@code @EnableApidoc(paramType = ApiStrategyType.DATABASE)}:自动配置出的策略能被 ApidocContext 取到,保存后能查到。
 */
class EnableApidocDatabaseTest {

    @Configuration
    @EnableApidoc(paramType = ApiStrategyType.DATABASE)
    static class DatabaseApp {
        @Bean
        DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:enable_apidoc_db;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            return ds;
        }
    }

    @Test
    void databaseParamTypeUsesTheApplicationsDataSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApidocAutoConfiguration.class, DatabaseAutoConfiguration.class))
                .withUserConfiguration(DatabaseApp.class)
                .withPropertyValues("csap.apidoc.database.poll-interval=0", "spring.application.name=e2e")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ApidocContext.paramStrategy(ApiStrategyType.DATABASE))
                            .isInstanceOf(DatabaseParamGroupStrategy.class);

                    StrategyModel model = StrategyModel.builder().paramType(ApiStrategyType.DATABASE).build();
                    ParamGroupMethodProperty p = new ParamGroupMethodProperty();
                    p.getRequest().put("name", new ParamGroupMethodProperty.ParamDataValidate(true, true, ParamType.QUERY, "姓名", null));
                    Map<String, ParamGroupMethodProperty> methods = new HashMap<>();
                    methods.put("add", p);
                    Map<String, Map<String, ParamGroupMethodProperty>> data = new HashMap<>();
                    data.put("a.B", methods);

                    ApidocContext.paramStrategy(ApiStrategyType.DATABASE)
                                 .writeAndMerge(model, data, ApiStrategyType.DATABASE, "a.B", "add", true);
                    CsapDocMethod m = new CsapDocMethod();
                    m.setClassName("a.B");
                    m.setName("add");
                    assertThat(ApidocContext.paramStrategy(ApiStrategyType.DATABASE)
                                            .paramDataValidate(model, "name", m, true).getDescription()).isEqualTo("姓名");
                });
    }

    @Configuration
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @org.springframework.context.annotation.Import(DbE2eController.class)
    @EnableApidoc(value = "ai.csap.apidoc.boot.autoconfigure.dbe2e", paramType = ApiStrategyType.DATABASE)
    static class DocApp {
        @Bean
        DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:enable_apidoc_doc;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            return ds;
        }
    }

    private static Boolean requiredOf(String param) {
        CsapDocResponse doc = ApidocContext.cmd(false, false);
        return doc.getApiList().stream().flatMap(c -> c.getMethodList().stream())
                  .filter(m -> "query".equals(m.getName()))
                  .flatMap(m -> m.getRequest().stream())
                  .flatMap(model -> model.getParameters().stream())
                  .filter(p -> param.equals(p.getName())).map(CsapDocParameter::getRequired).findFirst().orElse(null);
    }

    private void ruleChangedByAnotherInstance(boolean devtool) {
        new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApidocAutoConfiguration.class, DatabaseAutoConfiguration.class))
                .withUserConfiguration(DocApp.class)
                .withPropertyValues("csap.apidoc.database.poll-interval=0", "spring.application.name=e2e-doc",
                        "csap.apidoc.devtool.enabled=" + devtool)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    DatabaseParamGroupStrategy strategy = ctx.getBean(DatabaseParamGroupStrategy.class);
                    ApidocContext.cmd(true, false);
                    assertThat(requiredOf("keyword")).isNotEqualTo(Boolean.TRUE);

                    // 另一个实例直接改库:把 keyword 改成必传
                    DatabaseRuleStore other = new DatabaseRuleStore(ctx.getBean(DataSource.class),
                            strategy.store().dialect(), strategy.store().tablePrefix(), strategy.store().service());
                    other.save(DbE2eController.class.getName() + ".query",
                            "{\"request\":{\"keyword.keyword\":{\"required\":true,\"include\":true,\"description\":\"另一个实例改的\"}}}",
                            "other-instance");
                    assertThat(requiredOf("keyword")).as("没轮询前文档还是旧的").isNotEqualTo(Boolean.TRUE);

                    assertThat(strategy.pollOnce()).isTrue();
                    assertThat(requiredOf("keyword")).as("轮询重新加载后文档按新规则重新生成").isTrue();

                    // 回滚(这个实例发起)同样重新生成
                    long changeId = strategy.history(null, 1).get(0).getId();
                    strategy.rollback(changeId, "devtools-rollback");
                    assertThat(requiredOf("keyword")).isNotEqualTo(Boolean.TRUE);
                });
    }

    /**
     * 另一个实例改了规则:本实例轮询重新加载后,受影响类的文档和字段规则按新规则重新生成(DevTools 开启:按类刷新)。
     */
    @Test
    void ruleChangedByAnotherInstanceRegeneratesTheClassDocument() {
        ruleChangedByAnotherInstance(true);
    }

    /**
     * 生产环境关闭了 DevTools 时按类刷新不生效,改为整体重新扫描,同样要生效。
     */
    @Test
    void ruleChangedByAnotherInstanceRegeneratesTheDocumentWithoutDevtools() {
        ruleChangedByAnotherInstance(false);
    }
}
