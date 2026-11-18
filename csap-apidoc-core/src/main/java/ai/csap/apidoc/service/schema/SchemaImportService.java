package ai.csap.apidoc.service.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.model.ParamGroupMethodProperty.ParamDataValidate;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.service.schema.SchemaImporter.Change;
import ai.csap.apidoc.service.schema.SchemaImporter.Plan;
import ai.csap.apidoc.service.schema.SchemaImporter.Rule;
import ai.csap.apidoc.strategy.ParamGroupStrategy;
import ai.csap.apidoc.strategy.store.RuleActor;
import ai.csap.validation.factory.Validate;
import ai.csap.validation.type.ValidationStrategyType;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON Schema 导入:预览,或经现有规则保存路径写入(与 DevTools 保存同一条路径;数据库 / Redis 模式下修改人记
 * {@value #ACTOR})。
 *
 * @author ycf
 * @since 1.4.0
 */
public class SchemaImportService {

    public static final String ACTOR = "schema-import";

    private final Supplier<CsapDocResponse> docs;
    private final Supplier<EnableApidocConfig> config;

    public SchemaImportService(Supplier<CsapDocResponse> docs, Supplier<EnableApidocConfig> config) {
        this.docs = docs;
        this.config = config;
    }

    /**
     * 执行导入
     *
     * @param key     接口 key
     * @param request true 请求,false 返回
     * @param section 请求分区,可为 null
     * @param schema  JSON Schema
     * @param apply   true 写入,false 只预览
     * @return 结果(普通 Map / List,方便宿主的 JSON 序列化)
     */
    public Map<String, Object> run(String key, boolean request, String section, JsonNode schema, boolean apply) {
        CsapDocMethod method = find(key);
        if (method == null) {
            throw new IllegalArgumentException("文档里没有这个接口: " + key);
        }
        Plan plan = SchemaImporter.plan(method, request, section, schema, true);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key);
        out.put("kind", request ? "request" : "response");
        boolean applied = false;
        List<Map<String, String>> skipped = new ArrayList<>(plan.skipped);
        if (apply) {
            skipped.addAll(applyPlan(method, request, plan));
            applied = true;
        }
        out.put("applied", applied);
        out.put("rules", rules(plan));
        out.put("yaml", SchemaImporter.toYaml(method.getClassName(), method.getName(), plan));
        out.put("matched", plan.matched);
        out.put("unmatched", plan.unmatched);
        out.put("skipped", skipped);
        return out;
    }

    private CsapDocMethod find(String key) {
        CsapDocResponse doc = docs.get();
        if (doc != null && doc.getApiList() != null && key != null) {
            for (CsapDocModelController c : new ArrayList<>(doc.getApiList())) {
                if (c == null || c.getMethodList() == null) {
                    continue;
                }
                for (CsapDocMethod m : c.getMethodList()) {
                    if (m != null && key.equals(m.getKey())) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    private static Map<String, Object> rules(Plan plan) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Change c : plan.changes) {
            Map<String, Object> r = new LinkedHashMap<>();
            if (c.description != null) {
                r.put("description", c.description);
            }
            if (c.required != null) {
                r.put("required", c.required);
            }
            if (c.example != null) {
                r.put("example", c.example);
            }
            if (c.hasMock) {
                r.put("mock", c.mock);
            }
            if (c.validate != null) {
                List<Map<String, Object>> v = new ArrayList<>();
                for (Rule rule : c.validate) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("type", rule.type);
                    if (rule.pattern != null) {
                        e.put("pattern", rule.pattern);
                    }
                    if (rule.code != null) {
                        e.put("code", rule.code);
                    }
                    if (rule.level != null) {
                        e.put("level", rule.level);
                    }
                    if (rule.message != null) {
                        e.put("message", rule.message);
                    }
                    v.add(e);
                }
                r.put("validate", v);
            }
            if (!r.isEmpty()) {
                out.put(c.keyName, r);
            }
        }
        return out;
    }

    /**
     * 写入;返回写入时才发现的跳过项(返回字段没有规则条目)
     */
    private List<Map<String, String>> applyPlan(CsapDocMethod method, boolean request, Plan plan) {
        EnableApidocConfig cfg = config.get();
        if (cfg == null) {
            throw new IllegalStateException("没有可写的规则存储(未找到 @EnableApidoc 配置)");
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(method.getClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("找不到接口所在的类: " + method.getClassName());
        }
        StrategyModel sm = cfg.getApiPackageClass().stream().filter(i -> i.getClazz().contains(clazz)).findFirst()
                .orElseThrow(() -> new IllegalStateException("找不到接口所在类的规则来源配置"));
        if (sm.getParamType() == null || ApiStrategyType.ANNOTATION.equals(sm.getParamType())) {
            throw new IllegalStateException("当前规则来源是注解,不支持写规则;请改用 YAML / SQLite / 数据库 / Redis 模式,"
                    + "或把 yaml 片段粘贴进规则文件");
        }
        ParamGroupStrategy strategy = ApidocContext.paramStrategy(sm.getParamType());
        String className = method.getClassName();
        String methodName = method.getName();
        Map<String, Map<String, ParamGroupMethodProperty>> handle = strategy.handle(sm, sm.getParamType(),
                cfg.getPath(), className);
        Map<String, ParamGroupMethodProperty> classRules = handle == null ? null : handle.get(className);
        ParamGroupMethodProperty current = classRules == null ? null : classRules.get(methodName);
        // 在副本上改,保存成功后由策略自己更新缓存
        ParamGroupMethodProperty copy = new ParamGroupMethodProperty();
        copy.setStatus(current == null ? null : current.getStatus());
        copy.setStatusNote(current == null ? null : current.getStatusNote());
        copy.setRequest(copyAll(current == null ? null : current.getRequest()));
        copy.setResponse(copyAll(current == null ? null : current.getResponse()));
        Map<String, ParamDataValidate> side = request ? copy.getRequest() : copy.getResponse();
        List<Map<String, String>> skipped = new ArrayList<>();
        for (Change c : plan.changes) {
            ParamDataValidate rule = side.get(c.keyName);
            if (rule == null) {
                if (!request) {
                    // 给没有条目的返回字段新建条目会让该接口开始做返回字段过滤,不在这里做
                    skipped.add(entry("path", c.path, "reason", "返回字段在规则里没有条目,未写入(避免打开返回字段过滤)"));
                    continue;
                }
                rule = new ParamDataValidate();
                rule.setRequired(Boolean.FALSE);
                side.put(c.keyName, rule);
            }
            if (c.description != null) {
                rule.setDescription(c.description);
            }
            if (c.required != null) {
                rule.setRequired(c.required);
            }
            if (c.example != null) {
                rule.setExample(c.example);
            }
            if (c.hasMock) {
                rule.setMock(c.mock);
            }
            if (c.validate != null) {
                List<Validate.ConstraintValidatorField> list = new ArrayList<>();
                for (Rule r : c.validate) {
                    list.add(Validate.ConstraintValidatorField.builder()
                            .type(ValidationStrategyType.valueOf(r.type))
                            .pattern(r.pattern).code(r.code).message(r.message)
                            .level(r.level == null ? 1 : r.level).build());
                }
                rule.setValidate(java.util.Collections.synchronizedList(list));
            }
        }
        Map<String, ParamGroupMethodProperty> classCopy = classRules == null ? new HashMap<>()
                : new HashMap<>(classRules);
        classCopy.put(methodName, copy);
        Map<String, Map<String, ParamGroupMethodProperty>> dataMap = new HashMap<>();
        dataMap.put(className, classCopy);
        RuleActor.runAs(ACTOR, () -> {
            strategy.writeAndMerge(sm, dataMap, sm.getParamType(), className, methodName, request);
            return null;
        });
        try {
            ApidocContext.cmd(className, null, false);
        } catch (RuntimeException e) {
            // 规则已保存,文档刷新失败不影响结果
        }
        return skipped;
    }

    private static Map<String, ParamDataValidate> copyAll(Map<String, ParamDataValidate> src) {
        Map<String, ParamDataValidate> out = new LinkedHashMap<>();
        if (src != null) {
            src.forEach((k, v) -> out.put(k, v == null ? null : v.copyForUse()));
        }
        return out;
    }

    private static Map<String, String> entry(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
