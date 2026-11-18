package ai.csap.apidoc.mcp.tool;

import java.beans.PropertyDescriptor;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.beans.BeanUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.mcp.config.McpSettings;
import ai.csap.apidoc.mcp.config.ToolSetting;
import ai.csap.apidoc.mcp.protocol.JsonRpc;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.type.ModelType;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 工具目录:文档接口 + Spring MVC 映射 + 规则文件设置 → MCP 工具。
 *
 * <p>参数位置以 Spring 的方法参数注解为准(文档里路径变量常被记为 DEFAULT / BODY),
 * 说明、必填、类型和对象字段以文档为准。
 *
 * @author ycf
 * @since 1.3.0
 */
public final class ToolCatalog {

    /**
     * 工具名:兼容对工具名更严格的客户端(不含点号),最长 64
     */
    public static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    static final int MAX_NAME = 64;
    static final String BODY = "body";
    private static final ParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

    private final List<McpTool> all;
    private final Map<String, McpTool> exposedByName;

    private ToolCatalog(List<McpTool> all) {
        this.all = Collections.unmodifiableList(all);
        Map<String, McpTool> m = new TreeMap<>();
        for (McpTool t : all) {
            if (t.isExposed()) {
                m.put(t.getName(), t);
            }
        }
        this.exposedByName = Collections.unmodifiableMap(m);
    }

    public static ToolCatalog empty() {
        return new ToolCatalog(new ArrayList<>());
    }

    /**
     * 全部文档接口(含未暴露、不支持的),按类名、方法名排序
     */
    public List<McpTool> getAll() {
        return all;
    }

    /**
     * 暴露的工具,按工具名排序
     */
    public Map<String, McpTool> getExposed() {
        return exposedByName;
    }

    public McpTool byMethodKey(String key) {
        for (McpTool t : all) {
            if (t.getMethodKey().equals(key)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 构建目录
     *
     * @param doc                文档
     * @param handlers           Spring MVC 映射
     * @param settings           规则文件设置
     * @param exposeGetByDefault 未单独设置的 GET 接口是否暴露
     * @return 目录
     */
    public static ToolCatalog build(CsapDocResponse doc, HandlerIndex handlers, McpSettings settings,
                                    boolean exposeGetByDefault) {
        List<McpTool> tools = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        if (doc != null && doc.getApiList() != null) {
            for (CsapDocModelController c : doc.getApiList()) {
                if (c == null || c.getMethodList() == null) {
                    continue;
                }
                for (CsapDocMethod m : c.getMethodList()) {
                    if (m == null || m.getKey() == null || !seenKeys.add(m.getKey())) {
                        continue;
                    }
                    tools.add(toTool(c, m, handlers));
                }
            }
        }
        tools.sort(Comparator.comparing(McpTool::getMethodKey));
        applySettings(tools, settings == null ? McpSettings.empty() : settings, exposeGetByDefault);
        return new ToolCatalog(tools);
    }

    // ------------------------------------------------------------------ settings / names

    static void applySettings(List<McpTool> tools, McpSettings settings, boolean exposeGetByDefault) {
        Set<String> used = new HashSet<>();
        Map<McpTool, String> chosen = new HashMap<>();
        // 先给合法、不重名的改写名占位
        for (McpTool t : tools) {
            ToolSetting s = settings.tool(t.getMethodKey());
            String override = s == null || s.getName() == null ? null : s.getName().trim();
            if (override != null && NAME_PATTERN.matcher(override).matches()
                    && used.add(override.toLowerCase(Locale.ROOT))) {
                chosen.put(t, override);
            }
        }
        for (McpTool t : tools) {
            String name = chosen.get(t);
            if (name == null) {
                name = unique(t.getDefaultName(), used);
            }
            t.setName(name);
            ToolSetting s = settings.tool(t.getMethodKey());
            String desc = s == null || s.getDescription() == null || s.getDescription().trim().isEmpty() ? null
                    : s.getDescription().trim();
            t.setDescription(desc != null ? desc : t.getDefaultDescription());
            boolean byDefault = t.isReadOnly() && exposeGetByDefault;
            t.setExposedByDefault(byDefault);
            Boolean enabled = s == null ? null : s.getEnabled();
            t.setEnabledSetting(enabled);
            boolean usable = t.getUnsupportedReason() == null && !t.isHidden();
            t.setExposed(usable && (enabled != null ? enabled : byDefault));
        }
    }

    private static String unique(String base, Set<String> used) {
        String name = base;
        int i = 2;
        while (!used.add(name.toLowerCase(Locale.ROOT))) {
            String suffix = "_" + i++;
            String head = base.length() + suffix.length() > MAX_NAME ? base.substring(0, MAX_NAME - suffix.length()) : base;
            name = head + suffix;
        }
        return name;
    }

    /**
     * 默认工具名:{类简名去掉 Controller}_{方法名}
     *
     * @param simpleName 类简名
     * @param methodName 方法名
     * @return 工具名
     */
    public static String defaultName(String simpleName, String methodName) {
        String cls = simpleName == null ? "" : simpleName;
        if (cls.endsWith("Controller") && cls.length() > "Controller".length()) {
            cls = cls.substring(0, cls.length() - "Controller".length());
        }
        return sanitize(cls.isEmpty() ? methodName : cls + "_" + methodName);
    }

    static String sanitize(String raw) {
        String s = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_{2,}", "_");
        if (s.isEmpty() || "_".equals(s)) {
            s = "tool";
        }
        return s.length() > MAX_NAME ? s.substring(0, MAX_NAME) : s;
    }

    // ------------------------------------------------------------------ one tool

    private static McpTool toTool(CsapDocModelController c, CsapDocMethod m, HandlerIndex handlers) {
        McpTool t = new McpTool();
        t.setMethodKey(m.getKey());
        String className = m.getClassName() != null ? m.getClassName() : c.getName();
        t.setClassName(className);
        t.setMethodName(m.getName());
        t.setHidden(Boolean.TRUE.equals(m.getHidden()) || Boolean.TRUE.equals(c.getHidden()));
        t.setTitle(m.getValue());
        String simpleName = m.getSimpleName() != null ? m.getSimpleName() : c.getSimpleName();
        t.setDefaultName(defaultName(simpleName, m.getName()));

        String docMethod = m.getMethods() == null || m.getMethods().isEmpty() ? null : m.getMethods().get(0).name();
        HandlerIndex.Entry entry = pick(handlers.find(m.getKey()), docMethod);
        ObjectNode schema = JsonRpc.NODES.objectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        List<ParamBinding> bindings = new ArrayList<>();
        if (entry == null) {
            t.setUnsupportedReason("没有找到对应的 Spring MVC 映射");
            t.setHttpMethod(docMethod == null ? "GET" : docMethod);
        } else {
            String http = entry.getMethods().isEmpty() ? (docMethod == null ? "GET" : docMethod)
                    : (docMethod != null && entry.getMethods().contains(docMethod) ? docMethod
                    : entry.getMethods().iterator().next());
            t.setHttpMethod(http);
            if (entry.getPatterns().isEmpty()) {
                t.setUnsupportedReason("没有 URL 映射");
            } else {
                String pattern = entry.getPatterns().get(0);
                t.setPathPattern(pattern);
                if (pattern.replaceAll("\\{[^}]*}", "").contains("*")) {
                    t.setUnsupportedReason("URL 含通配符: " + pattern);
                }
            }
            String reason = bindParameters(entry.getHandlerMethod(), m, props, bindings);
            if (reason != null && t.getUnsupportedReason() == null) {
                t.setUnsupportedReason(reason);
            }
        }
        ArrayNode required = JsonRpc.NODES.arrayNode();
        for (ParamBinding b : bindings) {
            if (b.isRequired()) {
                required.add(b.getProperty());
            }
        }
        if (required.size() > 0) {
            schema.set("required", required);
        }
        t.setInputSchema(schema);
        t.setBindings(bindings);
        t.setDefaultDescription(defaultDescription(m, t));
        return t;
    }

    private static HandlerIndex.Entry pick(List<HandlerIndex.Entry> entries, String docMethod) {
        if (entries.isEmpty()) {
            return null;
        }
        if (docMethod != null) {
            for (HandlerIndex.Entry e : entries) {
                if (e.getMethods().contains(docMethod)) {
                    return e;
                }
            }
        }
        return entries.get(0);
    }

    static String defaultDescription(CsapDocMethod m, McpTool t) {
        StringBuilder sb = new StringBuilder();
        if (m.getValue() != null && !m.getValue().trim().isEmpty()) {
            sb.append(m.getValue().trim());
        }
        String d = m.getDescription();
        if (d != null && !d.trim().isEmpty() && !d.trim().equals(m.getValue())) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(d.trim());
        }
        if (t.getPathPattern() != null) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("HTTP ").append(t.getHttpMethod()).append(' ').append(t.getPathPattern());
        }
        return sb.length() == 0 ? t.getDefaultName() : sb.toString();
    }

    // ------------------------------------------------------------------ parameters

    /**
     * @return 不支持的原因;null 表示支持
     */
    private static String bindParameters(HandlerMethod hm, CsapDocMethod docMethod, ObjectNode props,
                                         List<ParamBinding> bindings) {
        Map<String, CsapDocModel> docByParam = new LinkedHashMap<>();
        if (docMethod.getRequest() != null) {
            for (CsapDocModel model : docMethod.getRequest()) {
                if (model != null && model.getMethodParamName() != null) {
                    docByParam.putIfAbsent(model.getMethodParamName(), model);
                }
            }
        }
        for (MethodParameter mp : hm.getMethodParameters()) {
            mp.initParameterNameDiscovery(NAMES);
            Class<?> type = mp.getParameterType();
            String pname = mp.getParameterName();
            String typeName = type.getName();
            if (isUnsupportedType(type, mp)) {
                return "不支持的参数类型: " + type.getSimpleName() + (pname == null ? "" : " " + pname);
            }
            if (isIgnoredType(type) || mp.hasParameterAnnotation(RequestAttribute.class)
                    || mp.hasParameterAnnotation(SessionAttribute.class)
                    || mp.hasParameterAnnotation(CookieValue.class)
                    || mp.hasParameterAnnotation(MatrixVariable.class)) {
                continue;
            }
            CsapDocModel doc = pname == null ? null : docByParam.get(pname);
            PathVariable pv = mp.getParameterAnnotation(PathVariable.class);
            if (pv != null) {
                if (java.util.Map.class.isAssignableFrom(type)) {
                    continue;
                }
                String name = firstNonEmpty(pv.value(), pv.name(), pname);
                add(props, bindings, name, ParamBinding.Location.PATH, name, true, simpleSchema(doc, type));
                continue;
            }
            RequestHeader rh = mp.getParameterAnnotation(RequestHeader.class);
            if (rh != null) {
                if (java.util.Map.class.isAssignableFrom(type) || typeName.endsWith("HttpHeaders")) {
                    continue;
                }
                String name = firstNonEmpty(rh.value(), rh.name(), pname);
                boolean req = rh.required() && ValueConstants.DEFAULT_NONE.equals(rh.defaultValue());
                add(props, bindings, name, ParamBinding.Location.HEADER, name, req || docRequired(doc),
                        simpleSchema(doc, type));
                continue;
            }
            RequestBody rb = mp.getParameterAnnotation(RequestBody.class);
            if (rb != null || HttpEntity.class.isAssignableFrom(type)) {
                ObjectNode s = bodySchema(doc, type);
                add(props, bindings, BODY, ParamBinding.Location.BODY, BODY, rb != null && rb.required(), s);
                continue;
            }
            RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
            if (rp != null) {
                if (java.util.Map.class.isAssignableFrom(type)) {
                    continue;
                }
                String name = firstNonEmpty(rp.value(), rp.name(), pname);
                boolean req = rp.required() && ValueConstants.DEFAULT_NONE.equals(rp.defaultValue());
                add(props, bindings, name, ParamBinding.Location.QUERY, name, req || docRequired(doc),
                        simpleSchema(doc, type));
                continue;
            }
            boolean simple = BeanUtils.isSimpleProperty(type)
                    || (type.isArray() && BeanUtils.isSimpleProperty(type.getComponentType()));
            if (simple || java.util.Collection.class.isAssignableFrom(type)) {
                if (pname == null) {
                    continue;
                }
                add(props, bindings, pname, ParamBinding.Location.QUERY, pname,
                        docRequired(doc) || type.isPrimitive(), simpleSchema(doc, type));
                continue;
            }
            if (java.util.Map.class.isAssignableFrom(type)) {
                // 无注解的 Map 是 Model
                continue;
            }
            if ("org.springframework.data.domain.Pageable".equals(typeName)) {
                add(props, bindings, "page", ParamBinding.Location.QUERY, "page", false, typed("integer", "页码(从 0 开始)"));
                add(props, bindings, "size", ParamBinding.Location.QUERY, "size", false, typed("integer", "每页条数"));
                add(props, bindings, "sort", ParamBinding.Location.QUERY, "sort", false, typed("string", "排序,如 id,desc"));
                continue;
            }
            // @ModelAttribute 或无注解的对象:字段作为查询参数
            if (mp.hasParameterAnnotation(ModelAttribute.class) || !simple) {
                expandQueryObject(props, bindings, doc, type);
            }
        }
        return null;
    }

    private static void expandQueryObject(ObjectNode props, List<ParamBinding> bindings, CsapDocModel doc,
                                          Class<?> type) {
        if (doc != null && doc.getParameters() != null && !doc.getParameters().isEmpty()) {
            expandFields(props, bindings, doc.getParameters(), "", 0);
            return;
        }
        for (PropertyDescriptor pd : BeanUtils.getPropertyDescriptors(type)) {
            if (pd.getWriteMethod() == null || pd.getPropertyType() == null) {
                continue;
            }
            Class<?> pt = pd.getPropertyType();
            if (BeanUtils.isSimpleProperty(pt) || java.util.Collection.class.isAssignableFrom(pt)) {
                add(props, bindings, pd.getName(), ParamBinding.Location.QUERY, pd.getName(), false,
                        SchemaBuilder.forClass(pt));
            }
        }
    }

    private static void expandFields(ObjectNode props, List<ParamBinding> bindings, List<CsapDocParameter> fields,
                                     String prefix, int depth) {
        for (CsapDocParameter f : fields) {
            if (f == null || f.getName() == null || Boolean.TRUE.equals(f.getHidden())) {
                continue;
            }
            String name = prefix + f.getName();
            List<CsapDocParameter> children = f.getParameters();
            boolean array = SchemaBuilder.isArrayType(f.getDataType()) || f.getModelType() == ModelType.ARRAY;
            if (children != null && !children.isEmpty() && !array && depth < 3) {
                expandFields(props, bindings, children, name + ".", depth + 1);
            } else {
                ObjectNode s = SchemaBuilder.forParameter(f, depth);
                add(props, bindings, name, ParamBinding.Location.QUERY, name, Boolean.TRUE.equals(f.getRequired()), s);
            }
        }
    }

    private static ObjectNode simpleSchema(CsapDocModel doc, Class<?> type) {
        CsapDocParameter p = single(doc);
        if (p != null) {
            ObjectNode s = SchemaBuilder.forParameter(p, 0);
            if (type.isEnum() && !s.has("enum")) {
                ObjectNode fromClass = SchemaBuilder.forClass(type);
                s.set("enum", fromClass.get("enum"));
            }
            return s;
        }
        ObjectNode s = SchemaBuilder.forClass(type);
        if (doc != null && doc.getValue() != null && !doc.getValue().isEmpty()) {
            s.put("description", doc.getValue());
        }
        return s;
    }

    private static ObjectNode bodySchema(CsapDocModel doc, Class<?> type) {
        if (doc != null) {
            CsapDocParameter p = single(doc);
            if (p != null && doc.getModelType() == ModelType.BASE_DATA) {
                return SchemaBuilder.forParameter(p, 0);
            }
            if (doc.getParameters() != null && !doc.getParameters().isEmpty()) {
                ObjectNode obj = SchemaBuilder.objectOf(doc.getParameters(), 1);
                ObjectNode s = obj;
                if (type.isArray() || java.util.Collection.class.isAssignableFrom(type)
                        || doc.getModelType() == ModelType.ARRAY) {
                    s = JsonRpc.NODES.objectNode();
                    s.put("type", "array");
                    s.set("items", obj);
                }
                describeModel(s, doc);
                return s;
            }
        }
        ObjectNode s = SchemaBuilder.forClass(type);
        if (doc != null) {
            describeModel(s, doc);
        }
        return s;
    }

    private static void describeModel(ObjectNode s, CsapDocModel doc) {
        String d = doc.getDescription() != null && !doc.getDescription().isEmpty() ? doc.getDescription() : doc.getValue();
        if (d != null && !d.isEmpty() && !d.equals(doc.getName())) {
            s.put("description", d);
        }
    }

    /**
     * BASE_DATA 模型只有一个同名参数
     */
    private static CsapDocParameter single(CsapDocModel doc) {
        if (doc == null || doc.getParameters() == null || doc.getParameters().size() != 1) {
            return null;
        }
        CsapDocParameter p = doc.getParameters().get(0);
        if (doc.getModelType() == ModelType.BASE_DATA
                || (doc.getMethodParamName() != null && doc.getMethodParamName().equals(p.getName()))) {
            return p;
        }
        return null;
    }

    private static boolean docRequired(CsapDocModel doc) {
        CsapDocParameter p = single(doc);
        return p != null && Boolean.TRUE.equals(p.getRequired());
    }

    private static ObjectNode typed(String type, String description) {
        ObjectNode s = JsonRpc.NODES.objectNode();
        s.put("type", type);
        s.put("description", description);
        return s;
    }

    private static void add(ObjectNode props, List<ParamBinding> bindings, String name, ParamBinding.Location loc,
                            String httpName, boolean required, ObjectNode schema) {
        String prop = name;
        if (props.has(prop)) {
            prop = name + "_" + loc.name().toLowerCase(Locale.ROOT);
            int i = 2;
            while (props.has(prop)) {
                prop = name + "_" + loc.name().toLowerCase(Locale.ROOT) + i++;
            }
        }
        if (loc == ParamBinding.Location.HEADER) {
            String d = schema.path("description").asText("");
            schema.put("description", (d.isEmpty() ? "" : d + "; ") + "请求头 " + httpName);
        }
        props.set(prop, schema);
        bindings.add(new ParamBinding(prop, loc, httpName, required));
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    static boolean isUnsupportedType(Class<?> type, MethodParameter mp) {
        String n = type.getName();
        if (mp.hasParameterAnnotation(RequestPart.class)) {
            return true;
        }
        if (n.startsWith("org.springframework.web.multipart.") || n.endsWith(".servlet.http.Part")) {
            return true;
        }
        if (type.isArray() && type.getComponentType().getName().startsWith("org.springframework.web.multipart.")) {
            return true;
        }
        return java.io.InputStream.class.isAssignableFrom(type) || java.io.Reader.class.isAssignableFrom(type);
    }

    static boolean isIgnoredType(Class<?> type) {
        String n = type.getName();
        return n.startsWith("javax.servlet.") || n.startsWith("jakarta.servlet.")
                || n.startsWith("org.springframework.ui.") || n.startsWith("org.springframework.validation.")
                || n.startsWith("org.springframework.web.context.request.")
                || n.startsWith("org.springframework.web.bind.support.")
                || n.startsWith("org.springframework.web.util.")
                || n.startsWith("org.springframework.web.servlet.")
                || "org.springframework.http.HttpMethod".equals(n)
                || Principal.class.isAssignableFrom(type)
                || java.util.Locale.class.equals(type) || java.util.TimeZone.class.equals(type)
                || java.time.ZoneId.class.isAssignableFrom(type)
                || java.io.OutputStream.class.isAssignableFrom(type) || java.io.Writer.class.isAssignableFrom(type);
    }
}
