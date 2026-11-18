package ai.csap.apidoc.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.strategy.ParamGroupStrategy;
import ai.csap.apidoc.strategy.store.RuleStoreParamGroupStrategy;
import ai.csap.apidoc.util.ValidateException;

/**
 * 字段模板:内置模板(随 jar,只读)+ 项目模板(跟着规则存储走),同名时项目模板覆盖内置。
 *
 * <ul>
 *   <li>数据库 / Redis:规则存储里的保留键 {@code @templates}(走版本号、修改记录、多实例同步);</li>
 *   <li>YAML / JSON / SQLite:{@code {path}/field-template/api-field-template.yaml};</li>
 *   <li>ANNOTATION:没有规则存储,只有内置模板。</li>
 * </ul>
 *
 * @author ycf
 * @since 1.4.0
 */
public class FieldTemplateService {

    /**
     * 内置模板在 jar 里的位置
     */
    public static final String BUILTIN_RESOURCE = "META-INF/csap-apidoc/field-templates.yaml";
    /**
     * 保存项目模板时记录的修改人
     */
    public static final String UPDATED_BY = "devtools";

    private static volatile List<FieldTemplate> builtin;

    private final EnableApidocConfig config;
    private volatile YamlFileFieldTemplateStore fileStore;

    public FieldTemplateService(EnableApidocConfig config) {
        this.config = config;
    }

    /**
     * 内置模板(只读)
     */
    public static List<FieldTemplate> builtin() {
        List<FieldTemplate> b = builtin;
        if (b == null) {
            try (InputStream in = FieldTemplateService.class.getClassLoader().getResourceAsStream(BUILTIN_RESOURCE)) {
                if (in == null) {
                    b = new ArrayList<>();
                } else {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    b = FieldTemplateCodec.fromYaml(new String(out.toByteArray(), StandardCharsets.UTF_8), BUILTIN_RESOURCE);
                }
            } catch (IOException e) {
                b = new ArrayList<>();
            }
            builtin = b;
        }
        return b;
    }

    /**
     * 当前的存储类型(数据库、Redis、YAML、SQLite),没有时(注解模式)为 null
     */
    private ApiStrategyType storeType() {
        if (config == null) {
            return null;
        }
        ApiStrategyType t = config.getParamType();
        if (t != null && t != ApiStrategyType.ANNOTATION) {
            return t;
        }
        for (StrategyModel m : config.getApiPackageClass()) {
            if (m.getParamType() != null && m.getParamType() != ApiStrategyType.ANNOTATION) {
                return m.getParamType();
            }
        }
        return null;
    }

    private RuleStoreParamGroupStrategy ruleStore() {
        ApiStrategyType t = storeType();
        if (t != ApiStrategyType.DATABASE && t != ApiStrategyType.REDIS) {
            return null;
        }
        ParamGroupStrategy s = ApidocContext.paramStrategy(t);
        return s instanceof RuleStoreParamGroupStrategy ? (RuleStoreParamGroupStrategy) s : null;
    }

    private YamlFileFieldTemplateStore file() {
        YamlFileFieldTemplateStore f = fileStore;
        if (f == null) {
            f = new YamlFileFieldTemplateStore(config == null ? null : config.getPath());
            fileStore = f;
        }
        return f;
    }

    /**
     * 是否支持保存项目模板
     */
    public boolean projectSupported() {
        ApiStrategyType t = storeType();
        return t != null;
    }

    /**
     * 项目模板
     */
    public List<FieldTemplate> project() {
        RuleStoreParamGroupStrategy rs = ruleStore();
        if (rs != null) {
            return FieldTemplateCodec.fromJson(rs.templatesJson(), "@templates");
        }
        ApiStrategyType t = storeType();
        return t == null ? new ArrayList<>() : file().load();
    }

    /**
     * 内置 + 项目模板,项目模板覆盖同名内置;每个带 source
     */
    public List<FieldTemplate> list() {
        Map<String, FieldTemplate> merged = new LinkedHashMap<>();
        for (FieldTemplate t : builtin()) {
            FieldTemplate c = FieldTemplateCodec.copy(t);
            c.setSource("builtin");
            merged.put(c.getName(), c);
        }
        for (FieldTemplate t : project()) {
            FieldTemplate c = FieldTemplateCodec.copy(t);
            c.setSource("project");
            merged.put(c.getName(), c);
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 新增或修改一个项目模板
     */
    public void saveProject(FieldTemplate template) {
        String problem = FieldTemplateCodec.check(template);
        if (problem != null) {
            throw new ValidateException("400", "字段模板不合法: " + problem);
        }
        List<FieldTemplate> projects = project();
        FieldTemplate copy = FieldTemplateCodec.copy(template);
        copy.setSource(null);
        boolean replaced = false;
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).getName().equals(copy.getName())) {
                projects.set(i, copy);
                replaced = true;
            }
        }
        if (!replaced) {
            projects.add(copy);
        }
        writeProject(projects);
    }

    /**
     * 删除一个项目模板;内置模板不能删除
     *
     * @return 是否删除了
     */
    public boolean deleteProject(String name) {
        List<FieldTemplate> projects = project();
        boolean removed = projects.removeIf(t -> t.getName().equals(name));
        if (removed) {
            writeProject(projects);
        }
        return removed;
    }

    private void writeProject(List<FieldTemplate> projects) {
        if (!projectSupported()) {
            throw new ValidateException("400", "当前是注解模式,没有规则存储,不能保存项目字段模板:请换用 YAML / SQLite / 数据库 / Redis 存储。");
        }
        RuleStoreParamGroupStrategy rs = ruleStore();
        if (rs != null) {
            rs.saveTemplatesJson(projects.isEmpty() ? null : FieldTemplateCodec.toJson(projects), UPDATED_BY);
        } else {
            file().save(projects);
        }
    }
}
