package ai.csap.apidoc.team.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** JSON 小工具:规范化(键排序 + 缩进)、解析。 */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    /** 规则 JSON 格式化(键排序、缩进),用于生成统一差异;不是合法 JSON 时原样返回。 */
    public static String pretty(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode n = MAPPER.readTree(json);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sort(n));
        } catch (IOException e) {
            return json;
        }
    }

    public static boolean isValid(String json) {
        try {
            return MAPPER.readTree(json) != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static JsonNode sort(JsonNode n) {
        if (n.isObject()) {
            ObjectNode out = JsonNodeFactory.instance.objectNode();
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = n.fields();
            while (it.hasNext()) {
                entries.add(it.next());
            }
            entries.sort(Map.Entry.comparingByKey(Comparator.naturalOrder()));
            for (Map.Entry<String, JsonNode> e : entries) {
                out.set(e.getKey(), sort(e.getValue()));
            }
            return out;
        }
        if (n.isArray()) {
            ArrayNode out = JsonNodeFactory.instance.arrayNode();
            n.forEach(x -> out.add(sort(x)));
            return out;
        }
        return n;
    }

    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonNode read(String s) {
        try {
            return MAPPER.readTree(s);
        } catch (IOException e) {
            return null;
        }
    }
}
