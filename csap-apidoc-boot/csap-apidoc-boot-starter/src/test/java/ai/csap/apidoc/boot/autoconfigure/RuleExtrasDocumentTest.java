package ai.csap.apidoc.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.boot.autoconfigure.dbe2e.DbE2eController;
import ai.csap.apidoc.database.DatabaseParamGroupStrategy;
import ai.csap.apidoc.database.autoconfigure.DatabaseAutoConfiguration;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.strategy.store.RuleJson;

/**
 * 规则里的 example、mock 透传到文档参数;接口的有效状态 = 规则覆盖 > 方法注解 > 类注解 > DEFAULT。
 */
class RuleExtrasDocumentTest {

    @Configuration
    @EnableWebMvc
    @Import(DbE2eController.class)
    @EnableApidoc(value = "ai.csap.apidoc.boot.autoconfigure.dbe2e", paramType = ApiStrategyType.DATABASE)
    static class App {
        @Bean
        DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:rule_extras_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            return ds;
        }
    }

    private static CsapDocMethod method(String name) {
        return ApidocContext.cmd(true, false).getApiList().stream().flatMap(c -> c.getMethodList().stream())
                            .filter(m -> name.equals(m.getName())).findFirst().orElseThrow();
    }

    private static CsapDocParameter keyword() {
        return method("query").getRequest().get(0).getParameters().get(0);
    }

    private static void save(DatabaseParamGroupStrategy s, String methodName, ParamGroupMethodProperty p) throws Exception {
        s.store().save(DbE2eController.class.getName() + "." + methodName, RuleJson.isEmpty(p) ? null : RuleJson.write(p), "test");
        s.reload();
    }

    @Test
    void exampleMockAndEffectiveStatusReachTheDocument() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApidocAutoConfiguration.class, DatabaseAutoConfiguration.class))
                .withUserConfiguration(App.class)
                .withPropertyValues("csap.apidoc.database.poll-interval=0", "csap.apidoc.devtool.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    DatabaseParamGroupStrategy s = ctx.getBean(DatabaseParamGroupStrategy.class);
                    // 没有规则:状态取注解(query 没标 → DEFAULT,save 标了 FINISH)
                    assertThat(method("query").getStatus()).isEqualTo(ApiStatus.DEFAULT);
                    assertThat(method("save").getStatus()).isEqualTo(ApiStatus.FINISH);
                    assertThat(method("query").getStatusNote()).isNull();

                    ParamGroupMethodProperty query = RuleJson.read("{\"request\":{\"keyword.keyword\":{\"required\":true,\"include\":true,"
                            + "\"description\":\"关键字\",\"example\":\"abc\",\"template\":\"kw\",\"mock\":{\"@oneOf\":[\"a\",\"b\"]}}},"
                            + "\"status\":\"DEPRECATED\",\"statusNote\":\"请改用 /v2\"}");
                    save(s, "query", query);
                    CsapDocParameter p = keyword();
                    assertThat(p.getExample()).isEqualTo("abc");
                    assertThat(p.getMock()).isEqualTo(Map.of("@oneOf", java.util.List.of("a", "b")));
                    assertThat(method("query").getStatus()).isEqualTo(ApiStatus.DEPRECATED);
                    assertThat(method("query").getStatusNote()).isEqualTo("请改用 /v2");

                    // 规则覆盖注解:save 明确标成开发中
                    save(s, "save", RuleJson.read("{\"status\":\"DEFAULT\"}"));
                    assertThat(method("save").getStatus()).isEqualTo(ApiStatus.DEFAULT);
                    // 清除覆盖后回到注解的 FINISH(只有状态、没有字段规则的接口是一条有效规则,清除才是删除)
                    save(s, "save", new ParamGroupMethodProperty());
                    assertThat(method("save").getStatus()).isEqualTo(ApiStatus.FINISH);
                    // 只有状态的规则会保存下来,不被当成空规则删掉
                    save(s, "save", RuleJson.read("{\"status\":\"DEPRECATED\"}"));
                    assertThat(s.store().load().rules()).containsKey(DbE2eController.class.getName() + ".save");
                    assertThat(method("save").getStatus()).isEqualTo(ApiStatus.DEPRECATED);
                });
    }
}
