package ai.csap.apidoc.service.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.type.ModelType;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON Schema → 字段规则(规则见 docs/design/1.4.0-mock-schema.md 第 5 节)。
 *
 * <p>只更新文档里已存在字段的说明、必填、示例、校验、Mock 值;不创建字段、不改类型。纯计算,不写任何存储。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class SchemaImporter {

    /** Java 规则能存取的校验类型(其余类型来自 Go 或手写,Java 的规则模型存不下) */
    public static final Set<String> JAVA_RULE_TYPES = new HashSet<>(Arrays.asList("NotNull", "NotEmpty", "Pattern"));
    private static final Set<String> UNSUPPORTED = new HashSet<>(Arrays.asList("$ref", "oneOf", "anyOf", "not",
            "if", "then", "else", "patternProperties", "additionalProperties"));

    private SchemaImporter() {
    }

    /** 一条校验规则 */
    public static final class Rule {
        public final String type;
        public final String pattern;
        public final String message;
        public final String code;
        public final Integer level;

        Rule(String type, String pattern, String message, String code, Integer level) {
            this.type = type;
            this.pattern = pattern;
            this.message = message;
            this.code = code;
            this.level = level;
        }
    }

    /** 一个字段的规则变更;值为 null 的项表示 Schema 没有提供,保持原规则不变 */
    public static final class Change {
        public String side;
        public String keyName;
        public String path;
        public String description;
        public Boolean required;
        public String example;
        public List<Rule> validate;
        public boolean hasMock;
        public Object mock;
    }

    /** 导入计划 */
    public static final class Plan {
        public final List<Change> changes = new ArrayList<>();
        public final List<Map<String, String>> unmatched = new ArrayList<>();
        public final List<Map<String, String>> skipped = new ArrayList<>();
        public int matched;
    }

    /**
     * 计算导入计划
     *
     * @param method  文档里的接口
     * @param request true 导入请求,false 导入返回
     * @param section 请求的分区(path/query/header/form/body);schema 是导出形状时可为 null
     * @param schema  JSON Schema
     * @param javaRules true 表示目标规则模型是 Java 的:不支持的校验类型进 skipped
     * @return 计划
     */
    public static Plan plan(CsapDocMethod method, boolean request, String section, JsonNode schema,
                            boolean javaRules) {
        Plan plan = new Plan();
        if (schema == null || !schema.isObject()) {
            throw new IllegalArgumentException("schema 必须是 JSON 对象");
        }
        String side = request ? "request" : "response";
        if (request) {
            Map<String, List<CsapDocParameter>> secs = requestSections(method);
            JsonNode props = schema.path("properties");
            boolean exportShape = section == null || section.isEmpty();
            if (exportShape) {
                boolean any = false;
                for (String s : SchemaExporter.SECTIONS) {
                    if (props.has(s)) {
                        any = true;
                    }
                }
                if (!any) {
                    throw new IllegalArgumentException(
                            "请求 Schema 不是导出形状(顶层 properties 应含 path/query/header/form/body),请用 section 指定分区");
                }
                for (String s : SchemaExporter.SECTIONS) {
                    if (props.has(s)) {
                        walk(side, secs.getOrDefault(s, new ArrayList<>()), allFields(secs.values()), objectOf(props.get(s)),
                                s, plan, javaRules);
                    }
                }
            } else {
                if (!Arrays.asList(SchemaExporter.SECTIONS).contains(section)) {
                    throw new IllegalArgumentException("section 只能是 path/query/header/form/body");
                }
                walk(side, secs.getOrDefault(section, new ArrayList<>()), allFields(secs.values()), objectOf(schema),
                        section, plan, javaRules);
            }
        } else {
            List<CsapDocParameter> fields = new ArrayList<>();
            if (method.getResponse() != null && !method.getResponse().isEmpty() && method.getResponse().get(0) != null
                    && method.getResponse().get(0).getModelType() != ModelType.BASE_DATA
                    && method.getResponse().get(0).getParameters() != null) {
                fields = method.getResponse().get(0).getParameters();
            }
            walk(side, fields, allFields(java.util.Collections.singletonList(fields)), objectOf(schema), "", plan,
                    javaRules);
        }
        return plan;
    }

    // ------------------------------------------------------------------ 内部

    private static Map<String, List<CsapDocParameter>> requestSections(CsapDocMethod method) {
        String verb = DocPaths.verb(method);
        Map<String, List<CsapDocParameter>> secs = new LinkedHashMap<>();
        if (method.getRequest() != null) {
            for (CsapDocModel md : method.getRequest()) {
                if (md == null || md.getParameters() == null) {
                    continue;
                }
                for (CsapDocParameter p : md.getParameters()) {
                    if (p == null || Boolean.TRUE.equals(p.getHidden()) || p.getName() == null) {
                        continue;
                    }
                    secs.computeIfAbsent(SchemaExporter.location(p, verb), k -> new ArrayList<>()).add(p);
                }
            }
        }
        return secs;
    }

    private static List<CsapDocParameter> allFields(Iterable<? extends List<CsapDocParameter>> lists) {
        List<CsapDocParameter> out = new ArrayList<>();
        for (List<CsapDocParameter> l : lists) {
            collect(l, out);
        }
        return out;
    }

    private static void collect(List<CsapDocParameter> l, List<CsapDocParameter> out) {
        for (CsapDocParameter p : l) {
            if (p == null) {
                continue;
            }
            out.add(p);
            collect(DocTypes.children(p), out);
        }
    }

    /** 数组 Schema 取 items;对象 Schema 原样 */
    private static JsonNode objectOf(JsonNode s) {
        JsonNode cur = s;
        for (int i = 0; i < 4 && cur != null && "array".equals(cur.path("type").asText()) && cur.has("items"); i++) {
            cur = cur.get("items");
        }
        return cur;
    }

    private static void walk(String side, List<CsapDocParameter> docFields, List<CsapDocParameter> all, JsonNode obj,
                             String prefix, Plan plan, boolean javaRules) {
        if (obj == null || !obj.path("properties").isObject()) {
            return;
        }
        Set<String> required = new HashSet<>();
        for (JsonNode r : obj.path("required")) {
            required.add(r.asText());
        }
        Iterator<Map.Entry<String, JsonNode>> it = obj.get("properties").fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String name = e.getKey();
            JsonNode s = e.getValue();
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            CsapDocParameter p = find(name, s, docFields, all);
            if (p == null) {
                plan.unmatched.add(entry("path", path, "reason", "字段不在文档中"));
                continue;
            }
            if (p.getKeyName() == null || p.getKeyName().isEmpty()) {
                plan.unmatched.add(entry("path", path, "reason", "文档字段没有规则键(keyName),不能写规则"));
                continue;
            }
            plan.matched++;
            Change c = new Change();
            c.side = side;
            c.keyName = p.getKeyName();
            c.path = path;
            fill(c, s, required.contains(name), plan, path, javaRules);
            plan.changes.add(c);
            JsonNode inner = objectOf(s);
            if (inner != null && inner.path("properties").isObject()) {
                walk(side, DocTypes.children(p), all, inner, path, plan, javaRules);
            }
        }
    }

    private static CsapDocParameter find(String name, JsonNode s, List<CsapDocParameter> docFields,
                                         List<CsapDocParameter> all) {
        String key = s.path("x-csap-key").asText("");
        if (!key.isEmpty()) {
            for (CsapDocParameter p : all) {
                if (key.equals(p.getKeyName())) {
                    return p;
                }
            }
        }
        for (CsapDocParameter p : docFields) {
            if (p != null && name.equals(p.getName()) && !Boolean.TRUE.equals(p.getHidden())) {
                return p;
            }
        }
        return null;
    }

    private static void fill(Change c, JsonNode s, boolean required, Plan plan, String path, boolean javaRules) {
        for (String k : UNSUPPORTED) {
            if (s.has(k)) {
                plan.skipped.add(entry("path", path, "keyword", k, "reason", "不支持的关键字"));
            }
        }
        if (s.path("description").isTextual() && !s.get("description").asText().isEmpty()) {
            c.description = s.get("description").asText();
        }
        if (required) {
            c.required = Boolean.TRUE;
        }
        JsonNode ex = s.path("examples").isArray() && s.get("examples").size() > 0 ? s.get("examples").get(0)
                : s.get("example");
        if (ex != null && ex.isValueNode() && !ex.isNull()) {
            c.example = ex.asText();
        }
        if (s.has("x-csap-mock")) {
            c.hasMock = true;
            c.mock = new com.fasterxml.jackson.databind.ObjectMapper().convertValue(s.get("x-csap-mock"), Object.class);
        }
        List<Rule> rules = null;
        if (s.path("x-csap-validate").isArray()) {
            rules = new ArrayList<>();
            for (JsonNode v : s.get("x-csap-validate")) {
                rules.add(new Rule(text(v, "type"), text(v, "pattern"), text(v, "message"), text(v, "code"),
                        v.has("level") ? v.get("level").asInt(1) : Integer.valueOf(1)));
            }
        } else {
            List<Rule> derived = derive(s);
            if (!derived.isEmpty()) {
                rules = derived;
            }
        }
        if (rules != null) {
            List<Rule> kept = new ArrayList<>();
            for (Rule r : rules) {
                String type = r.type == null ? "" : DocTypes.validateType(r.type);
                if (javaRules && !JAVA_RULE_TYPES.contains(type)) {
                    plan.skipped.add(entry("path", path, "keyword", type.isEmpty() ? "validate" : type,
                            "reason", "Java 规则模型不支持该校验类型(支持 NotNull、NotEmpty、Pattern)"));
                } else {
                    kept.add(new Rule(type, r.pattern, r.message, r.code, r.level));
                }
            }
            c.validate = kept;
        }
    }

    /** 标准关键字 → 校验规则(没有 x-csap-validate 时) */
    private static List<Rule> derive(JsonNode s) {
        List<Rule> out = new ArrayList<>();
        range(s, "minLength", "maxLength", "Length", out);
        range(s, "minItems", "maxItems", "Size", out);
        range(s, "minProperties", "maxProperties", "Size", out);
        if (s.path("minimum").isNumber()) {
            out.add(rule("Min", num(s.get("minimum"))));
        }
        if (s.path("maximum").isNumber()) {
            out.add(rule("Max", num(s.get("maximum"))));
        }
        if (s.path("pattern").isTextual()) {
            out.add(rule("Pattern", s.get("pattern").asText()));
        }
        if (s.path("enum").isArray() && s.get("enum").size() > 0) {
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (JsonNode n : s.get("enum")) {
                if (!n.isValueNode() || n.isNull()) {
                    ok = false;
                    break;
                }
                sb.append(sb.length() == 0 ? "" : ",").append(n.asText());
            }
            if (ok) {
                out.add(rule("Enum", sb.toString()));
            }
        }
        return out;
    }

    private static void range(JsonNode s, String minKey, String maxKey, String type, List<Rule> out) {
        boolean hasMin = s.path(minKey).isInt() || s.path(minKey).isLong();
        boolean hasMax = s.path(maxKey).isInt() || s.path(maxKey).isLong();
        if (!hasMin && !hasMax) {
            return;
        }
        String pattern = (hasMin ? s.get(minKey).asText() : "0") + "," + (hasMax ? s.get(maxKey).asText() : "");
        out.add(rule(type, pattern));
    }

    private static Rule rule(String type, String pattern) {
        return new Rule(type, pattern, null, "1001", 1);
    }

    private static String num(JsonNode n) {
        double d = n.asDouble();
        return d == Math.rint(d) && Math.abs(d) < 9e15 ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String text(JsonNode n, String key) {
        return n.path(key).isTextual() ? n.get(key).asText() : null;
    }

    private static Map<String, String> entry(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /**
     * 把计划渲染成 method-field.yaml 的片段(类名 → 方法名 → request/response → 规则键 → 规则)
     *
     * @param className  类名
     * @param methodName 方法名
     * @param plan       计划
     * @return YAML 文本
     */
    public static String toYaml(String className, String methodName, Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(q(className)).append(":\n  ").append(q(methodName)).append(":\n");
        for (String side : new String[]{"request", "response"}) {
            boolean header = false;
            for (Change c : plan.changes) {
                if (!side.equals(c.side)) {
                    continue;
                }
                if (!header) {
                    sb.append("    ").append(side).append(":\n");
                    header = true;
                }
                sb.append("      ").append(q(c.keyName)).append(":\n");
                if (c.description != null) {
                    sb.append("        description: ").append(q(c.description)).append('\n');
                }
                if (c.required != null) {
                    sb.append("        required: true\n");
                }
                if (c.example != null) {
                    sb.append("        example: ").append(q(c.example)).append('\n');
                }
                if (c.hasMock) {
                    sb.append("        mock: ").append(json(c.mock)).append('\n');
                }
                if (c.validate != null) {
                    if (c.validate.isEmpty()) {
                        sb.append("        validate: []\n");
                    } else {
                        sb.append("        validate:\n");
                        for (Rule r : c.validate) {
                            sb.append("          - type: ").append(q(r.type)).append('\n');
                            if (r.pattern != null) {
                                sb.append("            pattern: ").append(q(r.pattern)).append('\n');
                            }
                            if (r.code != null) {
                                sb.append("            code: ").append(q(r.code)).append('\n');
                            }
                            if (r.level != null) {
                                sb.append("            level: ").append(r.level).append('\n');
                            }
                            if (r.message != null) {
                                sb.append("            message: ").append(q(r.message)).append('\n');
                            }
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    /** JSON 字符串字面量同时是合法的 YAML 双引号标量 */
    private static String q(String s) {
        return json(s == null ? "" : s);
    }

    private static String json(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
