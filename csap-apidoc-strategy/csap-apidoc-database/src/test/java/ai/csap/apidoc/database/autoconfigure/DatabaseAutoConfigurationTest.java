package ai.csap.apidoc.database.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.database.DatabaseDialect;
import ai.csap.apidoc.database.DatabaseParamGroupStrategy;
import ai.csap.apidoc.model.CsapDocMethod;

class DatabaseAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DatabaseAutoConfiguration.class));

    private static DataSource h2(String name) {
        return new DataSource() {
            private final String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";

            @Override
            public Connection getConnection() throws java.sql.SQLException {
                return DriverManager.getConnection(url, "sa", "");
            }

            @Override
            public Connection getConnection(String u, String p) throws java.sql.SQLException {
                return getConnection();
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
            }

            @Override
            public void setLoginTimeout(int seconds) {
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                return null;
            }

            @Override
            public <T> T unwrap(Class<T> iface) {
                return null;
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class OneDataSource {
        @Bean
        DataSource dataSource() {
            return h2("auto_one");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDataSources {
        @Bean
        DataSource primaryDs() {
            return h2("auto_two_a");
        }

        @Bean
        DataSource reportDs() {
            return h2("auto_two_b");
        }
    }

    private static final StrategyModel MODEL = StrategyModel.builder().paramType(ApiStrategyType.DATABASE).build();

    private static CsapDocMethod method() {
        CsapDocMethod m = new CsapDocMethod();
        m.setClassName("a.B");
        m.setName("c");
        return m;
    }

    @Test
    void beanIsRegisteredWithoutDataSourceAndOnlyFailsWhenUsed() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            DatabaseParamGroupStrategy strategy = ctx.getBean(DatabaseParamGroupStrategy.class);
            assertThat(strategy.getName()).isEqualTo("database");
            assertThatThrownBy(() -> strategy.getHandle(MODEL, method()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DataSource");
        });
    }

    @Test
    void usesTheApplicationsDataSourceAndAppName() {
        runner.withUserConfiguration(OneDataSource.class)
              .withPropertyValues("spring.application.name=order-service", "csap.apidoc.database.table-prefix=ac1_",
                      "csap.apidoc.database.poll-interval=0")
              .run(ctx -> {
                  DatabaseParamGroupStrategy strategy = ctx.getBean(DatabaseParamGroupStrategy.class);
                  assertThat(strategy.getHandle(MODEL, method())).isEmpty();
                  assertThat(strategy.store().service()).isEqualTo("order-service");
                  assertThat(strategy.store().tablePrefix()).isEqualTo("ac1_");
                  assertThat(strategy.store().dialect()).isEqualTo(DatabaseDialect.H2);
                  try (Connection c = ctx.getBean(DataSource.class).getConnection(); Statement st = c.createStatement();
                       ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ac1_meta WHERE service = 'order-service'")) {
                      rs.next();
                      assertThat(rs.getInt(1)).isEqualTo(1);
                  }
              });
    }

    @Test
    void serviceFallsBackToDefaultAndCanBeSet() {
        runner.withUserConfiguration(OneDataSource.class).withPropertyValues("csap.apidoc.database.table-prefix=ac2_")
              .run(ctx -> assertThat(ctx.getBean(DatabaseParamGroupStrategy.class).store().service()).isEqualTo("default"));
        runner.withUserConfiguration(OneDataSource.class)
              .withPropertyValues("csap.apidoc.database.table-prefix=ac3_", "spring.application.name=x",
                      "csap.apidoc.database.service=custom")
              .run(ctx -> assertThat(ctx.getBean(DatabaseParamGroupStrategy.class).store().service()).isEqualTo("custom"));
    }

    @Test
    void severalDataSourcesNeedAName() {
        runner.withUserConfiguration(TwoDataSources.class).run(ctx -> {
            DatabaseParamGroupStrategy strategy = ctx.getBean(DatabaseParamGroupStrategy.class);
            assertThatThrownBy(() -> strategy.getHandle(MODEL, method()))
                    .hasMessageContaining("datasource-bean");
        });
        runner.withUserConfiguration(TwoDataSources.class)
              .withPropertyValues("csap.apidoc.database.datasource-bean=reportDs", "csap.apidoc.database.table-prefix=ac4_")
              .run(ctx -> {
                  DatabaseParamGroupStrategy strategy = ctx.getBean(DatabaseParamGroupStrategy.class);
                  assertThat(strategy.getHandle(MODEL, method())).isEmpty();
                  try (Connection c = ctx.getBean("reportDs", DataSource.class).getConnection();
                       Statement st = c.createStatement();
                       ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ac4_meta")) {
                      rs.next();
                      assertThat(rs.getInt(1)).isEqualTo(1);
                  }
              });
    }

    @Test
    void propertyDefaults() {
        DatabaseProperties p = new DatabaseProperties();
        assertThat(p.getTablePrefix()).isEqualTo("csap_apidoc_");
        assertThat(p.isInitSchema()).isTrue();
        assertThat(p.isReadOnly()).isFalse();
        assertThat(p.getPollInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(p.getDialect()).isNull();
        assertThat(p.getService()).isNull();
    }
}
