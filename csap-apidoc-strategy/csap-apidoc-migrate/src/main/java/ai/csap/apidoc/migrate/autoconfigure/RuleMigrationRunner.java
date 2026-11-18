package ai.csap.apidoc.migrate.autoconfigure;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.migrate.RuleMigrator;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.strategy.SQLiteParamGroupStrategy;
import ai.csap.apidoc.strategy.store.RuleStoreParamGroupStrategy;

/**
 * 启动时执行规则迁移并打印报告({@code csap.apidoc.migrate.from} 设置后生效)。
 * 参数不合法或来源 / 目标不可用时抛出说明清楚的异常,应用启动失败,避免误以为迁移成功。
 *
 * @author ycf
 * @since 1.4.0
 */
public class RuleMigrationRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RuleMigrationRunner.class);

    private final MigrateProperties properties;
    private final ApplicationContext context;
    private volatile RuleMigrator.Report lastReport;

    public RuleMigrationRunner(MigrateProperties properties, ApplicationContext context) {
        this.properties = properties;
        this.context = context;
    }

    /**
     * 最近一次导入的报告(没有导入时为 null)
     */
    public RuleMigrator.Report lastReport() {
        return lastReport;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String from = kind(properties.getFrom(), "from");
        String to = properties.getTo() == null || properties.getTo().trim().isEmpty() ? null : kind(properties.getTo(), "to");
        String exportTo = properties.getExportTo() == null || properties.getExportTo().trim().isEmpty()
                ? null : properties.getExportTo().trim();
        if (to == null && exportTo == null) {
            throw new IllegalStateException("规则迁移需要目标:设置 csap.apidoc.migrate.to=database|redis(导入),"
                    + "或 csap.apidoc.migrate.export-to=<目录>(导出成 YAML)。");
        }
        if (to != null && !isStore(to)) {
            throw new IllegalStateException("csap.apidoc.migrate.to 只能是 database 或 redis: " + to);
        }
        if (from.equals(to)) {
            throw new IllegalStateException("来源和目标相同: " + from);
        }
        if (exportTo != null && !isStore(from)) {
            throw new IllegalStateException("导出成 YAML 的来源只能是 database 或 redis(来源 " + from + " 本来就是文件)。");
        }

        LOG.info("csap-apidoc: 开始规则迁移 from={} to={} export-to={} overwrite={} dry-run={}", from, to, exportTo,
                properties.isOverwrite(), properties.isDryRun());
        Map<String, Map<String, ParamGroupMethodProperty>> rules = read(from);
        LOG.info("csap-apidoc: 从 {} 读到 {} 个类 {} 个接口的规则", from, rules.size(),
                rules.values().stream().mapToInt(Map::size).sum());

        if (to != null) {
            RuleStoreParamGroupStrategy target = strategy(to);
            if (target.isReadOnly()) {
                throw new IllegalStateException("目标 " + to + " 处于只读模式(read-only=true),不能导入。");
            }
            RuleMigrator.Report report = RuleMigrator.importRules(rules, target.store(), properties.isOverwrite(),
                    properties.isDryRun());
            lastReport = report;
            LOG.info("csap-apidoc: {}", report.text());
            if (!properties.isDryRun()) {
                // 立即重新加载并按新规则重新生成文档;其他实例靠订阅 / 轮询同步
                target.reload();
            }
        }
        if (exportTo != null) {
            File file = RuleMigrator.exportYaml(rules, new File(exportTo), properties.isDryRun());
            LOG.info("csap-apidoc: {}导出 YAML: {}", properties.isDryRun() ? "[演练,未写入] " : "已", file.getAbsolutePath());
        }
    }

    private static boolean isStore(String kind) {
        return "database".equals(kind) || "redis".equals(kind);
    }

    private static String kind(String value, String property) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("csap.apidoc.migrate." + property + " 不能为空");
        }
        String v = value.trim().toLowerCase(Locale.ROOT).replace("-", "_");
        if ("sqlite".equals(v)) {
            v = "sql_lite";
        }
        switch (v) {
            case "yaml":
            case "sql_lite":
            case "database":
            case "redis":
                return v;
            default:
                throw new IllegalStateException("csap.apidoc.migrate." + property + " 不支持 " + value
                        + ",可选 yaml / sql_lite / database / redis");
        }
    }

    private Map<String, Map<String, ParamGroupMethodProperty>> read(String from) throws Exception {
        if (isStore(from)) {
            RuleStoreParamGroupStrategy s = strategy(from);
            return RuleMigrator.readStore(s.store());
        }
        EnableApidocConfig cfg = context.getBeanProvider(EnableApidocConfig.class).getIfAvailable();
        if (cfg == null) {
            throw new IllegalStateException("没有找到 @EnableApidoc 配置,无法确定 " + from + " 规则的位置。");
        }
        if ("yaml".equals(from)) {
            return RuleMigrator.readYaml(cfg);
        }
        SQLiteParamGroupStrategy sqlite = context.getBeanProvider(SQLiteParamGroupStrategy.class)
                                                 .getIfAvailable(() -> new SQLiteParamGroupStrategy(null));
        String fileName = cfg.getFileName() == null || cfg.getFileName().trim().isEmpty()
                ? SQLiteHandle.DEFAULT_DB_NAME : cfg.getFileName();
        return sqlite.handle(RuleMigrator_sqliteModel(cfg.getPath(), fileName), ApiStrategyType.SQL_LITE, null, null);
    }

    private static ai.csap.apidoc.autoconfigure.StrategyModel RuleMigrator_sqliteModel(String path, String fileName) {
        return ai.csap.apidoc.autoconfigure.StrategyModel.builder().id("migrate-" + System.nanoTime())
                .paramType(ApiStrategyType.SQL_LITE).path(path).fileName(fileName).build();
    }

    private RuleStoreParamGroupStrategy strategy(String kind) {
        for (RuleStoreParamGroupStrategy s : context.getBeansOfType(RuleStoreParamGroupStrategy.class).values()) {
            if (kind.equals(s.getName())) {
                return s;
            }
        }
        throw new IllegalStateException("没有可用的 " + kind + " 规则存储:"
                + ("redis".equals(kind) ? "请引入 spring-boot-starter-data-redis 并配置 Redis 连接。"
                : "请确认已配置数据源(spring.datasource.*)。"));
    }
}
