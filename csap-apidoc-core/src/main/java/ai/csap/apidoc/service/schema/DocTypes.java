package ai.csap.apidoc.service.schema;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.type.ModelType;

/**
 * 文档字段类型归一(Mock 与 JSON Schema 导出共用,规则见 docs/design/1.4.0-mock-schema.md 2.5.2)。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class DocTypes {

    /**
     * 归一后的类型
     */
    public enum Kind {
        STRING, INTEGER, NUMBER, BOOLEAN, DATE, DATETIME, TIME, OBJECT, ARRAY
    }

    private static final Set<String> ARRAY_NAMES = new HashSet<>(Arrays.asList("list", "set", "collection",
            "iterable", "array", "arraylist", "linkedlist", "hashset", "linkedhashset", "treeset", "sortedset",
            "vector", "queue", "deque", "arraydeque"));
    private static final Set<String> INTEGERS = new HashSet<>(Arrays.asList("int", "integer", "int32", "short",
            "byte", "long", "int64", "biginteger", "atomicinteger", "atomiclong"));
    private static final Set<String> NUMBERS = new HashSet<>(Arrays.asList("double", "float", "float32", "float64",
            "bigdecimal", "number"));
    private static final Set<String> WIDE = new HashSet<>(Arrays.asList("long", "int64", "biginteger",
            "atomiclong"));
    private static final Set<String> DATETIMES = new HashSet<>(Arrays.asList("localdatetime", "date",
            "offsetdatetime", "zoneddatetime", "instant", "timestamp", "time"));

    private DocTypes() {
    }

    /**
     * 类型名 → kind。modelType 为 ARRAY 的字段由调用方另行按数组处理。
     *
     * @param dataType 文档里的 dataType
     * @return kind
     */
    public static Kind kind(String dataType) {
        String t = dataType == null ? "" : dataType.trim();
        if (t.endsWith("[]")) {
            return Kind.ARRAY;
        }
        int lt = t.indexOf('<');
        String raw = raw(t);
        if (ARRAY_NAMES.contains(raw) || lt > 0 && (raw.endsWith("list") || raw.endsWith("set"))) {
            return Kind.ARRAY;
        }
        if (INTEGERS.contains(raw)) {
            return Kind.INTEGER;
        }
        if (NUMBERS.contains(raw)) {
            return Kind.NUMBER;
        }
        if ("boolean".equals(raw) || "bool".equals(raw)) {
            return Kind.BOOLEAN;
        }
        if ("localdate".equals(raw)) {
            return Kind.DATE;
        }
        if (DATETIMES.contains(raw)) {
            return Kind.DATETIME;
        }
        if ("localtime".equals(raw)) {
            return Kind.TIME;
        }
        if (raw.endsWith("map") || "object".equals(raw) || "jsonnode".equals(raw) || "jsonobject".equals(raw)) {
            return Kind.OBJECT;
        }
        return Kind.STRING;
    }

    /**
     * 字段的 kind:modelType 为 ARRAY 时一律是数组
     *
     * @param p 字段
     * @return kind
     */
    public static Kind kind(CsapDocParameter p) {
        Kind k = kind(dataType(p));
        return k != Kind.ARRAY && p.getModelType() == ModelType.ARRAY ? Kind.ARRAY : k;
    }

    /**
     * 字段的类型名(dataType,没有时用 longDataType)
     *
     * @param p 字段
     * @return 类型名
     */
    public static String dataType(CsapDocParameter p) {
        return p.getDataType() != null ? p.getDataType() : p.getLongDataType();
    }

    /**
     * 数组的元素类型名,没有泛型时是 string
     *
     * @param dataType 数组的类型名
     * @return 元素类型名
     */
    public static String elementType(String dataType) {
        String t = dataType == null ? "" : dataType.trim();
        if (t.endsWith("[]")) {
            return t.substring(0, t.length() - 2);
        }
        int lt = t.indexOf('<');
        int gt = t.lastIndexOf('>');
        return lt > 0 && gt > lt ? t.substring(lt + 1, gt) : "string";
    }

    /**
     * 类型名去掉泛型、包名后转小写
     *
     * @param dataType 类型名
     * @return 简名(小写)
     */
    public static String raw(String dataType) {
        String t = dataType == null ? "" : dataType.trim();
        int lt = t.indexOf('<');
        String raw = lt > 0 ? t.substring(0, lt) : t;
        return raw.substring(raw.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }


    private static final java.util.regex.Pattern INT_TEXT = java.util.regex.Pattern.compile("[+-]?\\d+");
    private static final java.util.regex.Pattern NUM_TEXT = java.util.regex.Pattern
            .compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /**
     * 文本按类型转换:integer 要整数文本(Long),number 要有限小数(Double),boolean 要 true/false(忽略大小写);
     * 字符串类原样。转换失败返回 null。
     *
     * @param text 文本
     * @param kind 目标类型
     * @return 值或 null
     */
    public static Object coerce(String text, Kind kind) {
        if (text == null) {
            return null;
        }
        try {
            switch (kind) {
                case INTEGER: {
                    String s = text.trim();
                    return INT_TEXT.matcher(s).matches() ? (Object) Long.parseLong(s.startsWith("+") ? s.substring(1) : s) : null;
                }
                case NUMBER: {
                    String s = text.trim();
                    if (!NUM_TEXT.matcher(s).matches()) {
                        return null;
                    }
                    double v = Double.parseDouble(s);
                    return Double.isNaN(v) || Double.isInfinite(v) ? null : (Object) v;
                }
                case BOOLEAN: {
                    String s = text.trim().toLowerCase(Locale.ROOT);
                    return "true".equals(s) ? (Object) Boolean.TRUE : "false".equals(s) ? (Object) Boolean.FALSE : null;
                }
                case STRING:
                case DATE:
                case DATETIME:
                case TIME:
                    return text;
                default:
                    return null;
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 整数是否是 64 位
     *
     * @param dataType 类型名
     * @return true 表示 int64
     */
    public static boolean isWide(String dataType) {
        return WIDE.contains(raw(dataType));
    }

    /**
     * 校验规则的规范类型名。Java 文档里 validate.type 存的是中文描述(不能为空、不能为空字符串、正则表达式、
     * 注解验证、自定义验证),Go 与规则文件里是 NotNull、NotEmpty、Pattern 等;这里统一成后者。
     *
     * @param type 文档里的类型
     * @return 规范类型名;null 返回空串
     */
    public static String validateType(String type) {
        if (type == null) {
            return "";
        }
        String t = type.trim();
        switch (t) {
            case "不能为空":
                return "NotNull";
            case "不能为空字符串":
                return "NotEmpty";
            case "正则表达式":
                return "Pattern";
            case "注解验证":
                return "Annotation";
            case "自定义验证":
                return "customer";
            default:
                return t;
        }
    }

    /**
     * 字段的子字段;没有时返回空列表
     *
     * @param p 字段
     * @return 子字段
     */
    public static List<CsapDocParameter> children(CsapDocParameter p) {
        List<CsapDocParameter> c = p.getParameters();
        return c == null ? java.util.Collections.emptyList() : c;
    }

    /**
     * 范围文本 "最小,最大" / "n" / "n," 的解析结果
     */
    public static final class Range {
        public final int min;
        /** -1 表示无上限 */
        public final int max;

        Range(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    /**
     * 解析 Length / Size 的 pattern;不合法返回 null
     *
     * @param pattern pattern 文本
     * @return 范围
     */
    public static Range range(String pattern) {
        if (pattern == null) {
            return null;
        }
        try {
            String[] parts = pattern.split(",", 2);
            int lo = Integer.parseInt(parts[0].trim());
            if (parts.length == 1) {
                return new Range(lo, lo);
            }
            if (parts[1].trim().isEmpty()) {
                return new Range(lo, -1);
            }
            return new Range(lo, Integer.parseInt(parts[1].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
