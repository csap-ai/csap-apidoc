package ai.csap.apidoc.mcp.tool;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.csap.apidoc.mcp.protocol.JsonRpc;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.type.ModelType;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 文档参数 → JSON Schema
 *
 * @author ycf
 * @since 1.3.0
 */
public final class SchemaBuilder {

    /**
     * 对象嵌套深度上限(防止自引用类型无限展开)
     */
    static final int MAX_DEPTH = 8;

    private SchemaBuilder() {
    }

    /**
     * 文档参数的 schema(含子字段)
     *
     * @param p     文档参数
     * @param depth 当前深度
     * @return schema
     */
    public static ObjectNode forParameter(CsapDocParameter p, int depth) {
        String dataType = p.getDataType() != null ? p.getDataType() : p.getLongDataType();
        List<CsapDocParameter> children = p.getParameters();
        ObjectNode schema;
        if (children != null && !children.isEmpty() && depth < MAX_DEPTH) {
            ObjectNode obj = objectOf(children, depth + 1);
            if (isArrayType(dataType) || p.getModelType() == ModelType.ARRAY) {
                schema = JsonRpc.NODES.objectNode();
                schema.put("type", "array");
                schema.set("items", obj);
            } else {
                schema = obj;
            }
        } else {
            schema = forTypeName(dataType);
        }
        describe(schema, p);
        return schema;
    }

    /**
     * 字段列表 → object schema
     *
     * @param fields 字段
     * @param depth  深度
     * @return schema
     */
    public static ObjectNode objectOf(List<CsapDocParameter> fields, int depth) {
        ObjectNode obj = JsonRpc.NODES.objectNode();
        obj.put("type", "object");
        ObjectNode props = obj.putObject("properties");
        ArrayNode required = JsonRpc.NODES.arrayNode();
        for (CsapDocParameter f : fields) {
            if (f == null || f.getName() == null || Boolean.TRUE.equals(f.getHidden())) {
                continue;
            }
            props.set(f.getName(), forParameter(f, depth));
            if (Boolean.TRUE.equals(f.getRequired())) {
                required.add(f.getName());
            }
        }
        if (required.size() > 0) {
            obj.set("required", required);
        }
        return obj;
    }

    /**
     * 类型名(如 {@code Long}、{@code List<String>}、{@code int[]}、{@code java.util.Map})→ schema
     *
     * @param typeName 类型名
     * @return schema
     */
    public static ObjectNode forTypeName(String typeName) {
        ObjectNode s = JsonRpc.NODES.objectNode();
        if (typeName == null || typeName.trim().isEmpty()) {
            s.put("type", "string");
            return s;
        }
        String t = typeName.trim();
        if (isArrayType(t)) {
            s.put("type", "array");
            s.set("items", forTypeName(elementType(t)));
            return s;
        }
        String raw = simpleName(stripGenerics(t));
        String lower = raw.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "int":
            case "integer":
            case "long":
            case "short":
            case "byte":
            case "biginteger":
            case "atomicinteger":
            case "atomiclong":
                s.put("type", "integer");
                break;
            case "double":
            case "float":
            case "bigdecimal":
            case "number":
                s.put("type", "number");
                break;
            case "boolean":
            case "bool":
                s.put("type", "boolean");
                break;
            case "localdate":
                s.put("type", "string");
                s.put("format", "date");
                break;
            case "localdatetime":
            case "date":
            case "offsetdatetime":
            case "zoneddatetime":
            case "instant":
            case "timestamp":
                s.put("type", "string");
                s.put("format", "date-time");
                break;
            default:
                if (lower.endsWith("map") || "object".equals(lower) || "jsonnode".equals(lower)
                        || "jsonobject".equals(lower)) {
                    s.put("type", "object");
                } else {
                    s.put("type", "string");
                }
        }
        return s;
    }

    /**
     * Java 类型 → schema(方法参数在文档里找不到时使用)
     *
     * @param type 类型
     * @return schema
     */
    public static ObjectNode forClass(Class<?> type) {
        if (type.isArray()) {
            ObjectNode s = JsonRpc.NODES.objectNode();
            s.put("type", "array");
            s.set("items", forClass(type.getComponentType()));
            return s;
        }
        if (Collection.class.isAssignableFrom(type)) {
            ObjectNode s = JsonRpc.NODES.objectNode();
            s.put("type", "array");
            return s;
        }
        if (Map.class.isAssignableFrom(type)) {
            ObjectNode s = JsonRpc.NODES.objectNode();
            s.put("type", "object");
            return s;
        }
        if (type.isEnum()) {
            ObjectNode s = JsonRpc.NODES.objectNode();
            s.put("type", "string");
            ArrayNode values = s.putArray("enum");
            for (Object c : type.getEnumConstants()) {
                values.add(((Enum<?>) c).name());
            }
            return s;
        }
        if (!org.springframework.beans.BeanUtils.isSimpleProperty(type)) {
            ObjectNode s = JsonRpc.NODES.objectNode();
            s.put("type", "object");
            return s;
        }
        return forTypeName(type.getSimpleName());
    }

    static void describe(ObjectNode schema, CsapDocParameter p) {
        StringBuilder sb = new StringBuilder();
        String d = p.getDescription();
        if (d != null && !d.trim().isEmpty()) {
            sb.append(d.trim());
        }
        if (p.getExample() != null && !p.getExample().isEmpty()) {
            append(sb, "示例: " + p.getExample());
        }
        if (p.getValidate() != null) {
            for (CsapDocParameter.ValidateAttribute v : p.getValidate()) {
                if (v == null) {
                    continue;
                }
                String msg = v.getMessage() != null ? v.getMessage() : v.getDescr();
                if (v.getPattern() != null && !v.getPattern().isEmpty()) {
                    append(sb, "格式: " + v.getPattern() + (msg == null ? "" : " (" + msg + ")"));
                } else if (msg != null && !msg.isEmpty() && v.getType() != null) {
                    append(sb, v.getType() + ": " + msg);
                }
            }
        }
        if (sb.length() > 0) {
            schema.put("description", sb.toString());
        }
        if (p.getDefaultValue() != null && !p.getDefaultValue().isEmpty() && schema.path("type").isTextual()) {
            schema.put("default", p.getDefaultValue());
        }
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) {
            sb.append("; ");
        }
        sb.append(s);
    }

    static boolean isArrayType(String t) {
        if (t == null) {
            return false;
        }
        String s = t.trim();
        if (s.endsWith("[]")) {
            return true;
        }
        String raw = simpleName(stripGenerics(s)).toLowerCase(Locale.ROOT);
        return raw.endsWith("list") || raw.endsWith("set") || "collection".equals(raw) || "iterable".equals(raw);
    }

    static String elementType(String t) {
        String s = t.trim();
        if (s.endsWith("[]")) {
            return s.substring(0, s.length() - 2);
        }
        int lt = s.indexOf('<');
        int gt = s.lastIndexOf('>');
        if (lt > 0 && gt > lt) {
            return s.substring(lt + 1, gt);
        }
        return "string";
    }

    static String stripGenerics(String t) {
        int lt = t.indexOf('<');
        return lt > 0 ? t.substring(0, lt) : t;
    }

    static String simpleName(String t) {
        int dot = t.lastIndexOf('.');
        return dot >= 0 ? t.substring(dot + 1) : t;
    }
}
