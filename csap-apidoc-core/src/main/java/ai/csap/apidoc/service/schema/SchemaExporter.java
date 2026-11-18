package ai.csap.apidoc.service.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.service.schema.DocTypes.Kind;
import ai.csap.apidoc.type.ModelType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 文档 → JSON Schema 2020-12 导出(规则见 docs/design/1.4.0-mock-schema.md 第 4 节)。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class SchemaExporter {

    public static final String DRAFT = "https://json-schema.org/draft/2020-12/schema";
    public static final int MAX_DEPTH = 12;
    /** 请求分区,固定顺序 */
    public static final String[] SECTIONS = {"path", "query", "header", "form", "body"};
    static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SchemaExporter() {
    }

    // ------------------------------------------------------------------ 合集 / 单接口

    /**
     * 全部接口的合集文件
     *
     * @param doc 文档
     * @return 合集
     */
    public static ObjectNode bundle(CsapDocResponse doc) {
        ObjectNode root = F.objectNode();
        root.put("$schema", DRAFT);
        if (doc != null && doc.getApiInfo() != null && doc.getApiInfo().getTitle() != null) {
            root.put("title", doc.getApiInfo().getTitle());
        }
        root.put("x-csap-schema-version", 1);
        ArrayNode ops = root.putArray("x-csap-operations");
        ObjectNode defs = F.objectNode();
        Set<String> seen = new HashSet<>();
        if (doc != null && doc.getApiList() != null) {
            for (CsapDocModelController c : new ArrayList<>(doc.getApiList())) {
                if (c == null || Boolean.TRUE.equals(c.getHidden()) || c.getMethodList() == null) {
                    continue;
                }
                for (CsapDocMethod m : c.getMethodList()) {
                    if (!exportable(m) || !seen.add(m.getKey())) {
                        continue;
                    }
                    ObjectNode op = F.objectNode();
                    op.put("operationId", m.getKey());
                    op.put("method", DocPaths.verb(m));
                    op.put("path", DocPaths.fullPath(m, c));
                    op.put("summary", m.getValue() == null ? "" : m.getValue());
                    op.put("deprecated", m.getStatus() == ApiStatus.DEPRECATED);
                    ObjectNode req = request(m);
                    ObjectNode resp = response(m);
                    if (req == null) {
                        op.putNull("request");
                    } else {
                        req.remove("$schema");
                        defs.set(m.getKey() + ".request", req);
                        op.put("request", "#/$defs/" + m.getKey() + ".request");
                    }
                    if (resp == null) {
                        op.putNull("response");
                    } else {
                        resp.remove("$schema");
                        defs.set(m.getKey() + ".response", resp);
                        op.put("response", "#/$defs/" + m.getKey() + ".response");
                    }
                    ops.add(op);
                }
            }
        }
        root.set("$defs", defs);
        return root;
    }

    /**
     * 单个接口:operationId、method、path、summary、deprecated、request、response
     *
     * @param m          接口
     * @param controller 所在控制器,可为 null
     * @return 内容
     */
    public static ObjectNode method(CsapDocMethod m, CsapDocModelController controller) {
        ObjectNode out = F.objectNode();
        out.put("operationId", m.getKey());
        out.put("method", DocPaths.verb(m));
        out.put("path", DocPaths.fullPath(m, controller));
        out.put("summary", m.getValue() == null ? "" : m.getValue());
        out.put("deprecated", m.getStatus() == ApiStatus.DEPRECATED);
        ObjectNode req = request(m);
        ObjectNode resp = response(m);
        out.set("request", req == null ? F.nullNode() : req);
        out.set("response", resp == null ? F.nullNode() : resp);
        return out;
    }

    private static boolean exportable(CsapDocMethod m) {
        return m != null && m.getKey() != null && !Boolean.TRUE.equals(m.getHidden());
    }

    // ------------------------------------------------------------------ 请求 / 返回

    /**
     * 请求的 Schema(按位置分区,见设计文档 4.3);没有请求字段时返回 null
     *
     * @param method 接口
     * @return Schema 或 null
     */
    public static ObjectNode request(CsapDocMethod method) {
        String verb = DocPaths.verb(method);
        Map<String, List<CsapDocParameter>> secs = new LinkedHashMap<>();
        List<CsapDocParameter> whole = null;
        if (method.getRequest() != null) {
            for (CsapDocModel md : method.getRequest()) {
                if (md == null || md.getParameters() == null) {
                    continue;
                }
                List<CsapDocParameter> body = new ArrayList<>();
                for (CsapDocParameter p : md.getParameters()) {
                    if (skip(p)) {
                        continue;
                    }
                    String loc = location(p, verb);
                    secs.computeIfAbsent(loc, k -> new ArrayList<>()).add(p);
                    if ("body".equals(loc)) {
                        body.add(p);
                    }
                }
                if (md.getModelType() == ModelType.ARRAY && !body.isEmpty()) {
                    whole = body;
                    List<CsapDocParameter> rest = secs.get("body");
                    rest.removeAll(body);
                    if (rest.isEmpty()) {
                        secs.remove("body");
                    }
                }
            }
        }
        ObjectNode props = F.objectNode();
        ArrayNode req = F.arrayNode();
        for (String loc : SECTIONS) {
            if ("body".equals(loc) && whole != null) {
                ObjectNode arr = F.objectNode();
                arr.put("type", "array");
                arr.set("items", objectSchema(whole, 1));
                props.set("body", arr);
                req.add("body");
                continue;
            }
            List<CsapDocParameter> list = secs.get(loc);
            if (list != null) {
                ObjectNode o = objectSchema(list, 1);
                props.set(loc, o);
                if (o.has("required")) {
                    req.add(loc);
                }
            }
        }
        if (props.size() == 0) {
            return null;
        }
        ObjectNode s = F.objectNode();
        s.put("$schema", DRAFT);
        s.put("type", "object");
        s.set("properties", props);
        if (req.size() > 0) {
            s.set("required", req);
        }
        return s;
    }

    /**
     * 返回的 Schema(取第一个返回模型);没有返回模型时返回 null
     *
     * @param method 接口
     * @return Schema 或 null
     */
    public static ObjectNode response(CsapDocMethod method) {
        if (method.getResponse() == null || method.getResponse().isEmpty() || method.getResponse().get(0) == null) {
            return null;
        }
        CsapDocModel md = method.getResponse().get(0);
        List<CsapDocParameter> params = md.getParameters() == null ? new ArrayList<>() : md.getParameters();
        ObjectNode s;
        if (md.getModelType() == ModelType.BASE_DATA) {
            s = baseSchema(md.getName());
        } else if (md.getModelType() == ModelType.ARRAY) {
            s = F.objectNode();
            s.put("type", "array");
            s.set("items", objectSchema(params, 1));
        } else {
            s = objectSchema(params, 1);
        }
        ObjectNode out = F.objectNode();
        out.put("$schema", DRAFT);
        out.setAll(s);
        return out;
    }

    private static boolean skip(CsapDocParameter p) {
        return p == null || Boolean.TRUE.equals(p.getHidden()) || p.getName() == null || p.getName().isEmpty();
    }

    static String location(CsapDocParameter p, String verb) {
        ParamType pt = p.getParamType() == null ? ParamType.DEFAULT : p.getParamType();
        switch (pt) {
            case PATH:
                return "path";
            case QUERY:
                return "query";
            case HEADER:
                return "header";
            case FORM:
            case FORM_DATA:
                return "form";
            case BODY:
                return "body";
            default:
                return "GET".equals(verb) || "DELETE".equals(verb) || "HEAD".equals(verb) ? "query" : "body";
        }
    }

    // ------------------------------------------------------------------ 字段

    static ObjectNode objectSchema(List<CsapDocParameter> params, int depth) {
        ObjectNode props = F.objectNode();
        ArrayNode required = F.arrayNode();
        Set<String> req = new HashSet<>();
        for (CsapDocParameter p : params) {
            if (skip(p)) {
                continue;
            }
            props.set(p.getName(), fieldSchema(p, depth));
            if (Boolean.TRUE.equals(p.getRequired()) && req.add(p.getName())) {
                required.add(p.getName());
            }
        }
        ObjectNode s = F.objectNode();
        s.put("type", "object");
        s.set("properties", props);
        if (required.size() > 0) {
            s.set("required", required);
        }
        return s;
    }

    static ObjectNode fieldSchema(CsapDocParameter p, int depth) {
        List<CsapDocParameter> kids = DocTypes.children(p);
        Kind kind = DocTypes.kind(p);
        ObjectNode s;
        if (!kids.isEmpty() && depth < MAX_DEPTH) {
            ObjectNode obj = objectSchema(kids, depth + 1);
            s = kind == Kind.ARRAY ? arrayOf(obj) : obj;
        } else if (!kids.isEmpty()) {
            ObjectNode obj = F.objectNode();
            obj.put("type", "object");
            s = kind == Kind.ARRAY ? arrayOf(obj) : obj;
        } else if (kind == Kind.ARRAY) {
            s = arrayOf(baseSchema(DocTypes.elementType(DocTypes.dataType(p))));
        } else if ((p.getModelType() == ModelType.OBJECT || p.getModelType() == ModelType.T_OBJECT)
                && kind == Kind.STRING) {
            s = F.objectNode();
            s.put("type", "object");
        } else {
            s = baseSchema(DocTypes.dataType(p));
        }
        String d = p.getDescription();
        if (d == null || d.isEmpty()) {
            d = p.getValue();
        }
        if (d != null && !d.isEmpty()) {
            s.put("description", d);
        }
        String type = s.path("type").asText("");
        Kind ek = scalarKind(type);
        if (ek != null) {
            if (notEmpty(p.getExample())) {
                ArrayNode ex = s.putArray("examples");
                put(ex, DocTypes.coerce(p.getExample(), ek), p.getExample());
            }
            if (notEmpty(p.getDefaultValue())) {
                Object v = DocTypes.coerce(p.getDefaultValue(), ek);
                putValue(s, "default", v == null ? p.getDefaultValue() : v);
            }
        }
        applyValidate(s, p);
        if (!s.has("enum") && "string".equals(type) && p.getExtendDescr() != null) {
            ArrayNode names = F.arrayNode();
            for (Map<String, String> e : p.getExtendDescr()) {
                if (e != null && notEmpty(e.get("name"))) {
                    names.add(e.get("name"));
                }
            }
            if (names.size() > 0) {
                s.set("enum", names);
            }
        }
        ArrayNode xv = xValidate(p);
        if (xv.size() > 0) {
            s.set("x-csap-validate", xv);
        }
        if (notEmpty(p.getKeyName())) {
            s.put("x-csap-key", p.getKeyName());
        }
        return s;
    }

    private static Kind scalarKind(String type) {
        switch (type) {
            case "string":
                return Kind.STRING;
            case "integer":
                return Kind.INTEGER;
            case "number":
                return Kind.NUMBER;
            case "boolean":
                return Kind.BOOLEAN;
            default:
                return null;
        }
    }

    private static ObjectNode arrayOf(ObjectNode items) {
        ObjectNode a = F.objectNode();
        a.put("type", "array");
        a.set("items", items);
        return a;
    }

    /**
     * 类型名 → 基础 Schema(只有 type / format)
     *
     * @param dataType 类型名
     * @return Schema
     */
    public static ObjectNode baseSchema(String dataType) {
        Kind kind = DocTypes.kind(dataType);
        ObjectNode s = F.objectNode();
        String raw = DocTypes.raw(dataType);
        switch (kind) {
            case ARRAY:
                s.put("type", "array");
                s.set("items", baseSchema(DocTypes.elementType(dataType)));
                break;
            case INTEGER:
                s.put("type", "integer");
                s.put("format", DocTypes.isWide(dataType) ? "int64" : "int32");
                break;
            case NUMBER:
                s.put("type", "number");
                if ("float".equals(raw) || "float32".equals(raw)) {
                    s.put("format", "float");
                } else if (!"bigdecimal".equals(raw)) {
                    s.put("format", "double");
                }
                break;
            case BOOLEAN:
                s.put("type", "boolean");
                break;
            case DATE:
                s.put("type", "string");
                s.put("format", "date");
                break;
            case DATETIME:
                s.put("type", "string");
                s.put("format", "date-time");
                break;
            case TIME:
                s.put("type", "string");
                s.put("format", "time");
                break;
            case OBJECT:
                s.put("type", "object");
                break;
            default:
                s.put("type", "string");
        }
        return s;
    }

    // ------------------------------------------------------------------ 校验规则

    private static void applyValidate(ObjectNode s, CsapDocParameter p) {
        if (p.getValidate() == null) {
            return;
        }
        String t = s.path("type").asText("");
        List<String> pats = new ArrayList<>();
        for (CsapDocParameter.ValidateAttribute v : p.getValidate()) {
            if (v == null) {
                continue;
            }
            String vt = DocTypes.validateType(v.getType()).toLowerCase(Locale.ROOT);
            String pat = v.getPattern() == null ? "" : v.getPattern();
            switch (vt) {
                case "notempty":
                case "notblank":
                    if ("string".equals(t)) {
                        lo(s, "minLength", 1);
                    } else if ("array".equals(t)) {
                        lo(s, "minItems", 1);
                    } else if ("object".equals(t)) {
                        lo(s, "minProperties", 1);
                    }
                    break;
                case "length":
                case "size": {
                    DocTypes.Range r = DocTypes.range(pat);
                    String[] k = "string".equals(t) ? new String[]{"minLength", "maxLength"}
                            : "array".equals(t) ? new String[]{"minItems", "maxItems"}
                            : "object".equals(t) ? new String[]{"minProperties", "maxProperties"} : null;
                    if (r != null && k != null) {
                        lo(s, k[0], r.min);
                        if (r.max >= 0) {
                            hi(s, k[1], r.max);
                        }
                    }
                    break;
                }
                case "min":
                case "max":
                    if ("integer".equals(t) || "number".equals(t)) {
                        try {
                            double n = Double.parseDouble(pat.trim());
                            if (Double.isNaN(n) || Double.isInfinite(n)) {
                                break;
                            }
                            String key = "min".equals(vt) ? "minimum" : "maximum";
                            JsonNodeNumber cur = JsonNodeNumber.of(s.get(key));
                            double next = cur == null ? n : "min".equals(vt) ? Math.max(cur.value, n) : Math.min(cur.value, n);
                            putNumber(s, key, next);
                        } catch (NumberFormatException e) {
                            // 忽略不合法的边界
                        }
                    }
                    break;
                case "pattern":
                    if ("string".equals(t) && !pat.isEmpty() && !pat.startsWith("PATTERN_")) {
                        pats.add(pat);
                    }
                    break;
                case "enum": {
                    ArrayNode vals = F.arrayNode();
                    Kind ek = "integer".equals(t) ? Kind.INTEGER : "number".equals(t) ? Kind.NUMBER
                            : "boolean".equals(t) ? Kind.BOOLEAN : Kind.STRING;
                    for (String x : pat.split(",")) {
                        String item = x.trim();
                        if (item.isEmpty()) {
                            continue;
                        }
                        Object c = DocTypes.coerce(item, ek);
                        if (c != null) {
                            put(vals, c, item);
                        }
                    }
                    if (vals.size() > 0) {
                        s.set("enum", vals);
                    }
                    break;
                }
                default:
                    break;
            }
        }
        if (!pats.isEmpty()) {
            s.put("pattern", pats.get(0));
            if (pats.size() > 1) {
                ArrayNode all = s.putArray("allOf");
                for (String x : pats.subList(1, pats.size())) {
                    all.addObject().put("pattern", x);
                }
            }
        }
    }

    private static void lo(ObjectNode s, String key, int v) {
        s.put(key, s.has(key) ? Math.max(s.get(key).asInt(), v) : v);
    }

    private static void hi(ObjectNode s, String key, int v) {
        s.put(key, s.has(key) ? Math.min(s.get(key).asInt(), v) : v);
    }

    /** 已写入的 minimum / maximum 数值 */
    private static final class JsonNodeNumber {
        final double value;

        private JsonNodeNumber(double value) {
            this.value = value;
        }

        static JsonNodeNumber of(com.fasterxml.jackson.databind.JsonNode n) {
            return n == null || !n.isNumber() ? null : new JsonNodeNumber(n.asDouble());
        }
    }

    private static void putNumber(ObjectNode s, String key, double n) {
        if (n == Math.rint(n) && Math.abs(n) < 9e15) {
            s.put(key, (long) n);
        } else {
            s.put(key, n);
        }
    }

    private static ArrayNode xValidate(CsapDocParameter p) {
        ArrayNode out = F.arrayNode();
        if (p.getValidate() == null) {
            return out;
        }
        for (CsapDocParameter.ValidateAttribute v : p.getValidate()) {
            if (v == null) {
                continue;
            }
            ObjectNode e = F.objectNode();
            if (notEmpty(v.getType())) {
                e.put("type", DocTypes.validateType(v.getType()));
            }
            if (notEmpty(v.getPattern())) {
                e.put("pattern", v.getPattern());
            }
            if (notEmpty(v.getMessage())) {
                e.put("message", v.getMessage());
            }
            if (notEmpty(v.getCode())) {
                e.put("code", v.getCode());
            }
            if (v.getLevel() != null) {
                e.put("level", v.getLevel());
            }
            out.add(e);
        }
        return out;
    }

    private static void put(ArrayNode arr, Object v, String fallback) {
        Object x = v == null ? fallback : v;
        if (x instanceof Long) {
            arr.add((Long) x);
        } else if (x instanceof Double) {
            arr.add((Double) x);
        } else if (x instanceof Boolean) {
            arr.add((Boolean) x);
        } else {
            arr.add(String.valueOf(x));
        }
    }

    private static void putValue(ObjectNode s, String key, Object x) {
        if (x instanceof Long) {
            s.put(key, (Long) x);
        } else if (x instanceof Double) {
            s.put(key, (Double) x);
        } else if (x instanceof Boolean) {
            s.put(key, (Boolean) x);
        } else {
            s.put(key, String.valueOf(x));
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    /**
     * 序列化(紧凑 JSON)
     *
     * @param node 节点
     * @return 文本
     */
    public static String toJson(Object node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
