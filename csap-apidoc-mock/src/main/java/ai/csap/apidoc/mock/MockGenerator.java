package ai.csap.apidoc.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.service.schema.DocTypes;
import ai.csap.apidoc.service.schema.DocTypes.Kind;
import ai.csap.apidoc.type.ModelType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 按文档里的返回结构生成 Mock 数据,规则见 docs/design/1.4.0-mock-schema.md 2.5、2.6。
 * 不依赖 Spring;同一份文档 + 同一个种子,输出完全相同。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class MockGenerator {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ID_NAME = Pattern.compile("(?i:^id$)|.*[a-z0-9](Id|ID)$|.*_id$");
    private static final Pattern AT_INT = Pattern.compile("@int\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");
    private static final Pattern AT_ONE_OF = Pattern.compile("@oneOf\\((.*)\\)");
    private static final String[] NAMES = {"Alice", "Bob", "Carol", "David"};
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 1, 1);
    private static final long TWO_53 = 1L << 53;

    private final String seed;
    private final int arraySize;
    private final int maxDepth;
    private final boolean heuristics;

    /**
     * @param seed       种子
     * @param arraySize  数组默认长度(夹到 0~10)
     * @param maxDepth   对象嵌套深度上限(夹到 1~20)
     * @param heuristics 是否启用字段名启发式
     */
    public MockGenerator(String seed, int arraySize, int maxDepth, boolean heuristics) {
        this.seed = seed == null ? "csap" : seed;
        this.arraySize = Math.max(0, Math.min(10, arraySize));
        this.maxDepth = Math.max(1, Math.min(20, maxDepth));
        this.heuristics = heuristics;
    }

    /**
     * 生成接口的返回 JSON
     *
     * @param method 接口
     * @return 返回 JSON;接口没有返回模型时为 null
     */
    public JsonNode generate(CsapDocMethod method) {
        List<CsapDocModel> models = method.getResponse();
        if (models == null || models.isEmpty() || models.get(0) == null) {
            return null;
        }
        return generate(models.get(0));
    }

    /**
     * 生成一个返回模型的 JSON
     *
     * @param md 返回模型
     * @return JSON
     */
    public JsonNode generate(CsapDocModel md) {
        List<CsapDocParameter> params = md.getParameters() == null ? new ArrayList<>() : md.getParameters();
        if (md.getModelType() == ModelType.BASE_DATA) {
            CsapDocParameter p = new CsapDocParameter();
            p.setName("");
            return scalar(p, DocTypes.kind(md.getName()), "$");
        }
        if (md.getModelType() == ModelType.ARRAY) {
            ArrayNode arr = F.arrayNode();
            for (int i = 0; i < arraySize; i++) {
                arr.add(object(params, "$[" + i + "]", 1));
            }
            return arr;
        }
        return object(params, "$", 1);
    }

    // ------------------------------------------------------------------ 结构

    private ObjectNode object(List<CsapDocParameter> params, String path, int depth) {
        ObjectNode out = F.objectNode();
        for (CsapDocParameter p : params) {
            if (p == null || Boolean.TRUE.equals(p.getHidden()) || p.getName() == null || p.getName().isEmpty()) {
                continue;
            }
            out.set(p.getName(), field(p, path + "." + p.getName(), depth));
        }
        return out;
    }

    /**
     * @param depth 所在对象的深度(根对象为 1)
     */
    private JsonNode field(CsapDocParameter p, String path, int depth) {
        Kind kind = DocTypes.kind(p);
        List<CsapDocParameter> kids = DocTypes.children(p);
        Object mock = MockValues.of(p);
        if (mock != null) {
            return mockValue(mock, kind, MockRandom.of(seed, path));
        }
        if (kind == Kind.ARRAY) {
            int n = arrayLength(p);
            ArrayNode arr = F.arrayNode();
            if (!kids.isEmpty()) {
                if (depth + 1 > maxDepth) {
                    return arr;
                }
                for (int i = 0; i < n; i++) {
                    arr.add(object(kids, path + "[" + i + "]", depth + 1));
                }
                return arr;
            }
            String elem = DocTypes.elementType(DocTypes.dataType(p));
            Kind ek = DocTypes.kind(elem);
            for (int i = 0; i < n; i++) {
                CsapDocParameter ep = new CsapDocParameter();
                ep.setName(p.getName());
                ep.setDataType(elem);
                arr.add(scalar(ep, ek, path + "[" + i + "]"));
            }
            return arr;
        }
        if (!kids.isEmpty()) {
            if (depth + 1 > maxDepth) {
                return F.nullNode();
            }
            return object(kids, path, depth + 1);
        }
        return scalar(p, kind, path);
    }

    private int arrayLength(CsapDocParameter p) {
        int n = arraySize;
        for (String type : new String[]{"size", "length"}) {
            for (CsapDocParameter.ValidateAttribute v : validates(p, type)) {
                DocTypes.Range r = DocTypes.range(v.getPattern());
                if (r == null) {
                    continue;
                }
                n = Math.max(n, r.min);
                if (r.max >= 0) {
                    n = Math.min(n, r.max);
                }
            }
        }
        if (!validates(p, "notempty").isEmpty()) {
            n = Math.max(n, 1);
        }
        return Math.max(0, Math.min(n, 10));
    }

    // ------------------------------------------------------------------ 标量

    private JsonNode scalar(CsapDocParameter p, Kind kind, String path) {
        long r = MockRandom.of(seed, path);
        String name = heuristics && p.getName() != null ? p.getName() : "";
        String low = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        // 1 mock
        Object mock = MockValues.of(p);
        if (mock != null) {
            return mockValue(mock, kind, r);
        }
        // 2 example / 3 defaultValue
        for (String text : new String[]{p.getExample(), p.getDefaultValue()}) {
            if (text != null && !text.isEmpty()) {
                Object v = DocTypes.coerce(text, kind);
                if (v != null) {
                    return node(v);
                }
            }
        }
        // 4 枚举
        List<String> ev = enumValues(p);
        if (!ev.isEmpty()) {
            Object v = DocTypes.coerce(ev.get((int) MockRandom.mod(r, ev.size())), kind);
            if (v != null) {
                return node(v);
            }
        }
        // 5 约束 / 6 名称 / 7 类型
        switch (kind) {
            case INTEGER:
                return integer(p, name, low, r);
            case NUMBER:
                return number(p, r);
            case BOOLEAN:
                return F.booleanNode("success".equals(low) || MockRandom.mod(r, 2) == 0);
            case DATE:
                return F.textNode(date(r));
            case DATETIME:
                return F.textNode(date(r) + "T" + time(r) + "Z");
            case TIME:
                return F.textNode(time(r));
            case OBJECT:
                return F.objectNode();
            default:
                return F.textNode(string(p, name, low, r));
        }
    }

    private JsonNode integer(CsapDocParameter p, String name, String low, long r) {
        List<Double> mins = numbers(p, "min");
        List<Double> maxs = numbers(p, "max");
        if (mins.isEmpty() && maxs.isEmpty()) {
            if (!name.isEmpty() && ID_NAME.matcher(name).matches()) {
                return F.numberNode(1000 + MockRandom.mod(r, 9000));
            }
            switch (low) {
                case "code":
                    return F.numberNode(200L);
                case "page":
                case "pageno":
                case "pagenum":
                case "current":
                case "pageindex":
                    return F.numberNode(1L);
                case "pagesize":
                case "size":
                case "limit":
                case "perpage":
                    return F.numberNode(10L);
                case "age":
                    return F.numberNode(18 + MockRandom.mod(r, 60));
                default:
                    break;
            }
        }
        long lo = mins.isEmpty() ? 1 : (long) max(mins);
        long hi = maxs.isEmpty() ? (mins.isEmpty() ? 100 : lo + 99) : (long) min(maxs);
        if (hi < lo) {
            hi = lo;
        }
        long width = hi - lo + 1;
        if (width <= 0 || width > TWO_53) {
            width = TWO_53;
        }
        return F.numberNode(lo + MockRandom.mod(r, width));
    }

    private JsonNode number(CsapDocParameter p, long r) {
        List<Double> mins = numbers(p, "min");
        List<Double> maxs = numbers(p, "max");
        double lo = mins.isEmpty() ? 1.0 : max(mins);
        double hi = maxs.isEmpty() ? (mins.isEmpty() ? 100.0 : lo + 99) : min(maxs);
        if (hi < lo) {
            hi = lo;
        }
        long steps = (long) Math.rint((hi - lo) * 100);
        double v = lo + MockRandom.mod(r, steps + 1) / 100.0;
        return F.numberNode(BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_EVEN).doubleValue());
    }

    private String string(CsapDocParameter p, String name, String low, long r) {
        String s;
        switch (low) {
            case "message":
            case "msg":
                s = "ok";
                break;
            case "code":
                s = "200";
                break;
            case "email":
                s = "user" + MockRandom.mod(r, 1000) + "@example.com";
                break;
            case "phone":
            case "mobile":
            case "tel":
            case "telephone":
            case "cellphone":
                s = String.format("13%09d", MockRandom.mod(r, 1_000_000_000L));
                break;
            case "name":
            case "username":
            case "nickname":
            case "realname":
            case "fullname":
                s = NAMES[(int) MockRandom.mod(r, 4)];
                break;
            case "url":
            case "link":
            case "website":
            case "homepage":
            case "avatar":
            case "image":
            case "img":
            case "icon":
            case "logo":
            case "picture":
                s = "https://example.com/" + name;
                break;
            default:
                s = !name.isEmpty() && ID_NAME.matcher(name).matches() ? String.format("id-%08x", r >>> 32) : "string";
        }
        return fit(s, p);
    }

    /** Length / Size 约束:不足补 x,超出截断(按字符) */
    private static String fit(String s, CsapDocParameter p) {
        for (String type : new String[]{"length", "size"}) {
            for (CsapDocParameter.ValidateAttribute v : validates(p, type)) {
                DocTypes.Range range = DocTypes.range(v.getPattern());
                if (range == null) {
                    continue;
                }
                int len = s.codePointCount(0, s.length());
                if (len < range.min) {
                    StringBuilder sb = new StringBuilder(s);
                    for (int i = len; i < range.min; i++) {
                        sb.append('x');
                    }
                    s = sb.toString();
                    len = range.min;
                }
                if (range.max >= 0 && len > range.max) {
                    s = s.substring(0, s.offsetByCodePoints(0, range.max));
                }
            }
        }
        return s;
    }

    private static String date(long r) {
        return BASE_DATE.plusDays(MockRandom.mod(r, 365)).toString();
    }

    private static String time(long r) {
        long secs = MockRandom.mod(r >>> 16, 86400);
        return String.format("%02d:%02d:%02d", secs / 3600, secs % 3600 / 60, secs % 60);
    }

    // ------------------------------------------------------------------ mock 值

    private JsonNode mockValue(Object m, Kind kind, long r) {
        if (!(m instanceof String)) {
            return MAPPER.valueToTree(m);
        }
        String s = (String) m;
        if (s.startsWith("@@")) {
            return F.textNode(s.substring(1));
        }
        if (!s.startsWith("@")) {
            return F.textNode(s);
        }
        Matcher mi = AT_INT.matcher(s);
        if (mi.matches()) {
            long lo = Long.parseLong(mi.group(1));
            long hi = Long.parseLong(mi.group(2));
            return F.numberNode(hi >= lo ? lo + MockRandom.mod(r, hi - lo + 1) : lo);
        }
        Matcher mo = AT_ONE_OF.matcher(s);
        if (mo.matches()) {
            String[] items = mo.group(1).split(",", -1);
            String pick = items[(int) MockRandom.mod(r, items.length)].trim();
            Object v = DocTypes.coerce(pick, kind);
            return v != null ? node(v) : F.textNode(pick);
        }
        if ("@uuid".equals(s)) {
            String h = String.format("%016x%016x", r, MockRandom.splitmix64(r));
            return F.textNode(h.substring(0, 8) + "-" + h.substring(8, 12) + "-4" + h.substring(13, 16) + "-8"
                    + h.substring(17, 20) + "-" + h.substring(20, 32));
        }
        return F.textNode(s);
    }

    // ------------------------------------------------------------------ 辅助

    private static JsonNode node(Object v) {
        if (v instanceof Long) {
            return F.numberNode((Long) v);
        }
        if (v instanceof Double) {
            return F.numberNode((Double) v);
        }
        if (v instanceof Boolean) {
            return F.booleanNode((Boolean) v);
        }
        return F.textNode(String.valueOf(v));
    }

    private static List<CsapDocParameter.ValidateAttribute> validates(CsapDocParameter p, String type) {
        List<CsapDocParameter.ValidateAttribute> out = new ArrayList<>();
        if (p.getValidate() != null) {
            for (CsapDocParameter.ValidateAttribute v : p.getValidate()) {
                if (v != null && v.getType() != null && type.equals(v.getType().toLowerCase(Locale.ROOT))) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    private static List<Double> numbers(CsapDocParameter p, String type) {
        List<Double> out = new ArrayList<>();
        for (CsapDocParameter.ValidateAttribute v : validates(p, type)) {
            try {
                double d = Double.parseDouble(v.getPattern().trim());
                if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                    out.add(d);
                }
            } catch (RuntimeException e) {
                // 不合法的边界忽略
            }
        }
        return out;
    }

    private static double max(List<Double> l) {
        double m = l.get(0);
        for (double d : l) {
            m = Math.max(m, d);
        }
        return m;
    }

    private static double min(List<Double> l) {
        double m = l.get(0);
        for (double d : l) {
            m = Math.min(m, d);
        }
        return m;
    }

    private static List<String> enumValues(CsapDocParameter p) {
        for (CsapDocParameter.ValidateAttribute v : validates(p, "enum")) {
            List<String> vals = new ArrayList<>();
            if (v.getPattern() != null) {
                for (String x : v.getPattern().split(",")) {
                    if (!x.trim().isEmpty()) {
                        vals.add(x.trim());
                    }
                }
            }
            if (!vals.isEmpty()) {
                return vals;
            }
        }
        List<String> names = new ArrayList<>();
        if (p.getExtendDescr() != null) {
            for (java.util.Map<String, String> e : p.getExtendDescr()) {
                if (e != null && e.get("name") != null && !e.get("name").isEmpty()) {
                    names.add(e.get("name"));
                }
            }
        }
        return names;
    }
}
