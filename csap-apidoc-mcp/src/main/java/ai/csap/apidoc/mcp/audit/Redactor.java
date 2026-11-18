package ai.csap.apidoc.mcp.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import ai.csap.apidoc.mcp.protocol.JsonRpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 审计摘要的脱敏与截断:常见敏感字段(password、token、secret 等)的值替换为 ***。
 * 字段名比较忽略大小写并去掉 "-"、"_",按"包含"匹配(如 accessToken、user_password 都会打码)。
 *
 * @author ycf
 * @since 1.3.0
 */
public class Redactor {

    public static final String MASK = "***";

    static final List<String> BUILTIN = Arrays.asList("password", "passwd", "pwd", "secret", "token",
            "authorization", "apikey", "credential", "cookie", "session", "privatekey", "accesskey", "signature",
            "idcard", "bankcard", "cvv");

    private final List<String> needles = new ArrayList<>();
    private final int maxChars;

    public Redactor(Collection<String> extraFields, int maxChars) {
        needles.addAll(BUILTIN);
        if (extraFields != null) {
            for (String f : extraFields) {
                if (f != null && !f.trim().isEmpty()) {
                    needles.add(normalize(f));
                }
            }
        }
        this.maxChars = Math.max(32, maxChars);
    }

    static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    public boolean isSensitive(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String n = normalize(fieldName);
        for (String needle : needles) {
            if (n.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * JSON 摘要:递归打码后序列化并截断
     *
     * @param node JSON
     * @return 摘要
     */
    public String summarize(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return truncate(JsonRpc.write(redact(node.deepCopy())));
    }

    /**
     * 文本摘要:能解析为 JSON 的按 JSON 打码,否则原样截断
     *
     * @param text 文本
     * @return 摘要
     */
    public String summarizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            try {
                return summarize(JsonRpc.MAPPER.readTree(t));
            } catch (Exception ignore) {
                // 截断的或非 JSON,按文本处理
            }
        }
        return truncate(t);
    }

    JsonNode redact(JsonNode node) {
        if (node.isObject()) {
            ObjectNode o = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = o.fields();
            List<String> mask = new ArrayList<>();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (isSensitive(e.getKey()) && !e.getValue().isNull()) {
                    mask.add(e.getKey());
                } else {
                    redact(e.getValue());
                }
            }
            mask.forEach(k -> o.put(k, MASK));
        } else if (node.isArray()) {
            ArrayNode a = (ArrayNode) node;
            for (JsonNode item : a) {
                redact(item);
            }
        }
        return node;
    }

    public String truncate(String s) {
        if (s == null || s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "…(" + s.length() + " chars)";
    }
}
