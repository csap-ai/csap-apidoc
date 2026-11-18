package ai.csap.apidoc.database.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库模式配置(只有 {@code @EnableApidoc(paramType = ApiStrategyType.DATABASE)} 时才会用到)。
 *
 * <pre>
 * csap:
 *   apidoc:
 *     database:
 *       datasource-bean: dataSource      # 多个 DataSource 时指定用哪个
 *       table-prefix: csap_apidoc_
 *       poll-interval: 5s
 * </pre>
 *
 * @author ycf
 * @since 1.4.0
 */
@ConfigurationProperties(prefix = DatabaseProperties.PREFIX)
public class DatabaseProperties {

    public static final String PREFIX = "csap.apidoc.database";

    /**
     * 使用的 DataSource Bean 名称;为空时使用容器中唯一(或标记为 primary)的 DataSource。
     */
    private String datasourceBean;

    /**
     * 数据库方言:mysql、postgresql、dm、kingbase、sqlite、h2;为空时按 JDBC URL 识别。
     */
    private String dialect;

    /**
     * 表名前缀,只能包含字母、数字和下划线。
     */
    private String tablePrefix = "csap_apidoc_";

    /**
     * 表不存在时是否自动建表;关闭后请先执行 jar 内 META-INF/csap-apidoc/database/ 下的建表 SQL。
     */
    private boolean initSchema = true;

    /**
     * 轮询规则版本号的间隔,发现别的实例修改了规则就重新加载;0 表示不轮询。
     */
    private Duration pollInterval = Duration.ofSeconds(5);

    /**
     * 服务标识,同一套表里用它区分不同服务的规则;为空时用 spring.application.name,再为空用 default。
     */
    private String service;

    /**
     * 只读模式:只读取规则,不建表、不允许 DevTools 保存。
     */
    private boolean readOnly = false;

    public String getDatasourceBean() {
        return datasourceBean;
    }

    public void setDatasourceBean(String datasourceBean) {
        this.datasourceBean = datasourceBean;
    }

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public boolean isInitSchema() {
        return initSchema;
    }

    public void setInitSchema(boolean initSchema) {
        this.initSchema = initSchema;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
