package ai.csap.apidoc.migrate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.strategy.handle.MethodFieldHandle;
import ai.csap.apidoc.strategy.store.RuleJson;
import ai.csap.apidoc.strategy.store.RuleStore;

/**
 * 规则迁移:把 YAML 目录 / SQLite 文件的规则导入数据库或 Redis,或把数据库 / Redis 的规则导出成 YAML 目录。
 *
 * <p>导入的每一条都走存储的正常保存(修改人记为 {@value #UPDATED_BY}),所以有修改记录、可以回滚,
 * 其他实例也会收到同步通知。默认不覆盖已有规则:内容相同算"没变化",内容不同算"冲突"并跳过,在报告里列出。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class RuleMigrator {

    private static final Logger LOG = LoggerFactory.getLogger(RuleMigrator.class);

    /**
     * 导入时记录的修改人
     */
    public static final String UPDATED_BY = "migrate";

    /**
     * 导出的 YAML 文件,位于导出目录的 method-field 子目录下;YAML 策略读取时两种文件前缀模式都能匹配它
     */
    public static final String EXPORT_FILE = "method-field/api-method-field.yaml";

    private RuleMigrator() {
    }

    // ------------------------------------------------------------------ 读取

    /**
     * 读取 YAML 目录里的规则(按应用的 {@code @EnableApidoc} 配置从 classpath 读取)
     */
    public static Map<String, Map<String, ParamGroupMethodProperty>> readYaml(EnableApidocConfig config) {
        String prefix = config.getPrefixStrategy() == null ? "*" : config.getPrefixStrategy().getName();
        return new MethodFieldHandle(config).handle(ApiStrategyType.YAML, config.getPath(), prefix);
    }

    /**
     * 读取外部存储(数据库、Redis)里的全部规则
     */
    public static Map<String, Map<String, ParamGroupMethodProperty>> readStore(RuleStore store) throws Exception {
        Map<String, Map<String, ParamGroupMethodProperty>> out = new TreeMap<>();
        for (Map.Entry<String, String> e : store.load().rules().entrySet()) {
            String key = e.getKey();
            if (key.startsWith("@")) {
                continue; // 保留键(如 @templates 字段模板)不是接口规则
            }
            int dot = key.lastIndexOf('.');
            if (dot <= 0 || dot == key.length() - 1) {
                LOG.warn("csap-apidoc: 规则键格式不对,应为 类全名.方法名,已忽略: {}", key);
                continue;
            }
            try {
                out.computeIfAbsent(key.substring(0, dot), k -> new TreeMap<>()).put(key.substring(dot + 1), RuleJson.read(e.getValue()));
            } catch (IOException ex) {
                LOG.warn("csap-apidoc: 规则 {} 的 JSON 无法解析,已忽略: {}", key, ex.getMessage());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 导入

    /**
     * 把规则导入外部存储。
     *
     * @param source    规则:类名 → 方法名 → 规则
     * @param target    目标存储(调用前须已 {@link RuleStore#init})
     * @param overwrite 已有且内容不同的规则是否覆盖;false 时跳过并报告冲突
     * @param dryRun    只演练:计算结果但不写入
     * @return 报告
     */
    public static Report importRules(Map<String, Map<String, ParamGroupMethodProperty>> source, RuleStore target,
                                     boolean overwrite, boolean dryRun) throws Exception {
        Report report = new Report(dryRun, overwrite);
        Map<String, String> existing = target.load().rules();
        for (Map.Entry<String, Map<String, ParamGroupMethodProperty>> byClass : new TreeMap<>(source).entrySet()) {
            for (Map.Entry<String, ParamGroupMethodProperty> m : new TreeMap<>(byClass.getValue()).entrySet()) {
                String key = byClass.getKey() + "." + m.getKey();
                if (RuleJson.isEmpty(m.getValue())) {
                    report.empty.add(key);
                    continue;
                }
                String json = RuleJson.write(m.getValue());
                String current = existing.get(key);
                if (current == null) {
                    report.imported.add(key);
                } else if (RuleJson.sameJson(current, json)) {
                    report.unchanged.add(key);
                    continue;
                } else if (overwrite) {
                    report.overwritten.add(key);
                } else {
                    report.conflicts.add(key);
                    continue;
                }
                if (!dryRun) {
                    target.save(key, json, UPDATED_BY);
                }
            }
        }
        return report;
    }

    // ------------------------------------------------------------------ 导出

    /**
     * 把规则导出成 YAML 文件 {@code <dir>/method-field/api-method-field.yaml},格式与 DevTools 写出的规则文件一致。
     * 把 {@code dir} 的内容放进 {@code src/main/resources/csap-api-doc/} 就能用 YAML 策略读取。
     *
     * @param rules  规则
     * @param dir    导出目录
     * @param dryRun 只演练,不写文件
     * @return 导出的文件
     */
    public static File exportYaml(Map<String, Map<String, ParamGroupMethodProperty>> rules, File dir, boolean dryRun)
            throws IOException {
        File file = new File(dir, EXPORT_FILE);
        if (dryRun) {
            return file;
        }
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("无法创建目录 " + parent);
        }
        // 触发 YAML 策略的共用 YAMLMapper 的静态初始化(与 DevTools 写文件的配置一致)
        try {
            Class.forName("ai.csap.apidoc.strategy.AbstractHandle");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        YAMLMapper mapper = ai.csap.apidoc.strategy.Handle.YAML_MAPPER;
        mapper.writeValue(file, new TreeMap<>(sorted(rules)));
        return file;
    }

    private static Map<String, Map<String, ParamGroupMethodProperty>> sorted(Map<String, Map<String, ParamGroupMethodProperty>> rules) {
        Map<String, Map<String, ParamGroupMethodProperty>> out = new TreeMap<>();
        rules.forEach((c, ms) -> out.put(c, new TreeMap<>(ms)));
        return out;
    }

    /**
     * 迁移报告
     */
    public static final class Report {
        private final boolean dryRun;
        private final boolean overwrite;
        final List<String> imported = new ArrayList<>();
        final List<String> overwritten = new ArrayList<>();
        final List<String> unchanged = new ArrayList<>();
        final List<String> conflicts = new ArrayList<>();
        final List<String> empty = new ArrayList<>();

        Report(boolean dryRun, boolean overwrite) {
            this.dryRun = dryRun;
            this.overwrite = overwrite;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        /**
         * 新增(目标里原来没有)的规则键
         */
        public List<String> getImported() {
            return imported;
        }

        /**
         * 覆盖了已有规则的规则键(overwrite=true)
         */
        public List<String> getOverwritten() {
            return overwritten;
        }

        /**
         * 目标里已有且内容相同、不需要写的规则键
         */
        public List<String> getUnchanged() {
            return unchanged;
        }

        /**
         * 冲突:目标里已有且内容不同、没有覆盖而跳过的规则键
         */
        public List<String> getConflicts() {
            return conflicts;
        }

        /**
         * 来源里没有任何字段、已忽略的规则键
         */
        public List<String> getEmpty() {
            return empty;
        }

        public boolean hasConflicts() {
            return !conflicts.isEmpty();
        }

        /**
         * 多行文本报告
         */
        public String text() {
            StringBuilder sb = new StringBuilder();
            sb.append(dryRun ? "[演练 dry-run,未写入] " : "").append("规则迁移:新增 ").append(imported.size())
              .append(",覆盖 ").append(overwritten.size()).append(",已一致 ").append(unchanged.size())
              .append(",冲突(已跳过) ").append(conflicts.size()).append(",空规则(已忽略) ").append(empty.size());
            append(sb, dryRun ? "将新增" : "新增", imported);
            append(sb, dryRun ? "将覆盖" : "覆盖", overwritten);
            append(sb, "冲突:目标里已有不同的规则,未覆盖(要覆盖请设置 csap.apidoc.migrate.overwrite=true)", conflicts);
            return sb.toString();
        }

        private static void append(StringBuilder sb, String title, List<String> keys) {
            if (keys.isEmpty()) {
                return;
            }
            sb.append('\n').append("  ").append(title).append(':');
            keys.forEach(k -> sb.append('\n').append("    - ").append(k));
        }

        @Override
        public String toString() {
            return text();
        }
    }
}
