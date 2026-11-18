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
import ai.csap.apidoc.database.autoconfigure.DatabaseAutoConfiguration;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.service.ApidocContext;

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
}
