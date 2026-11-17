package ai.csap.apidoc.runtime.filter;

import java.util.function.BiFunction;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 测试用:用三种引擎把值按 FilterSession 序列化成 JSON
 */
final class Engines {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private static final ObjectMapper FILTERING = new ObjectMapper();

    static {
        JacksonFilterSupport.install(FILTERING);
    }

    private Engines() {
    }

    enum Engine {
        JACKSON(Engines::jackson),
        FASTJSON(Engines::fastjson),
        FASTJSON_NO_REF(Engines::fastjsonNoRef),
        FASTJSON2(Engines::fastjson2);

        final BiFunction<Object, FilterSession, String> writer;

        Engine(BiFunction<Object, FilterSession, String> writer) {
            this.writer = writer;
        }

        String write(Object value, FilterSession session) {
            return writer.apply(value, session);
        }
    }

    static String jackson(Object value, FilterSession session) {
        try {
            Object body = new JacksonResponseFilterEngine().apply(value, session);
            org.springframework.http.converter.json.MappingJacksonValue mjv =
                    (org.springframework.http.converter.json.MappingJacksonValue) body;
            return FILTERING.writer(mjv.getFilters()).writeValueAsString(mjv.getValue());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String fastjson(Object value, FilterSession session) {
        return com.alibaba.fastjson.JSON.toJSONString(new FastjsonResponseFilterEngine().apply(value, session));
    }

    static String fastjsonNoRef(Object value, FilterSession session) {
        return com.alibaba.fastjson.JSON.toJSONString(new FastjsonResponseFilterEngine().apply(value, session),
                SerializerFeature.DisableCircularReferenceDetect, SerializerFeature.WriteMapNullValue);
    }

    static String fastjson2(Object value, FilterSession session) {
        return com.alibaba.fastjson2.JSON.toJSONString(new Fastjson2ResponseFilterEngine().apply(value, session));
    }

    static JsonNode tree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(json, e);
        }
    }

    /**
     * JSON 文本转成保持键顺序的 Map / List
     */
    static Object parse(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException(json, e);
        }
    }

    static CompiledEndpoint endpoint(FieldMask mask, TypeRules rules) {
        return new CompiledEndpoint("demo.Controller.get", "demo.Controller", mask, rules);
    }

    static FilterSession path(FieldMask mask, Object original) {
        return new FilterSession(FilterMode.PATH, endpoint(mask, null), original);
    }

    static FilterSession csap(TypeRules rules, Object original) {
        return new FilterSession(FilterMode.CSAP, endpoint(null, rules), original);
    }
}
