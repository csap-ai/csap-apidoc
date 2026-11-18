package ai.csap.apidoc.migrate.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 规则迁移配置:设置 {@code from} 后在启动时执行迁移并打印报告。
 *
 * <pre>
 * # YAML 目录导入数据库(先 dry-run 看报告)
 * --csap.apidoc.migrate.from=yaml --csap.apidoc.migrate.to=database --csap.apidoc.migrate.dry-run=true
 * # 数据库导出成 YAML 目录
 * --csap.apidoc.migrate.from=database --csap.apidoc.migrate.export-to=./exported-rules
 * </pre>
 *
 * @author ycf
 * @since 1.4.0
 */
@ConfigurationProperties(prefix = MigrateProperties.PREFIX)
public class MigrateProperties {

    public static final String PREFIX = "csap.apidoc.migrate";

    /**
     * 来源:yaml、sql_lite、database、redis。为空表示不迁移。
     */
    private String from;

    /**
     * 目标存储:database、redis(导入)。
     */
    private String to;

    /**
     * 导出成 YAML 的目录(来源为 database / redis 时);文件为 {目录}/method-field/api-method-field.yaml。
     */
    private String exportTo;

    /**
     * 目标里已有且内容不同的规则是否覆盖;默认不覆盖,在报告里列出冲突。
     */
    private boolean overwrite = false;

    /**
     * 只演练:打印报告但不写入。
     */
    private boolean dryRun = false;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getExportTo() {
        return exportTo;
    }

    public void setExportTo(String exportTo) {
        this.exportTo = exportTo;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
