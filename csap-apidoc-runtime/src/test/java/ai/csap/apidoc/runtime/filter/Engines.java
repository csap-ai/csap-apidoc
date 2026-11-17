package ai.csap.apidoc.runtime.filter;

import java.util.function.BiFunction;

import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 测试用:用三种引擎把值按 FilterSession 序列化成 JSON
 */
final class Engines {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private static final JacksonResponseFilterEngine JACKSON_ENGINE = new JacksonResponseFilterEngine();

    static {
        JACKSON_ENGINE.register(MappingJackson2HttpMessageConverter.class, new ObjectMapper());
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

    /**
     * 模拟消息转换器:用未安装过滤的 ObjectMapper 写出引擎返回的对象
     */
    static String jackson(Object value, FilterSession session) {
        try {
            return MAPPER.writeValueAsString(JACKSON_ENGINE.apply(value, session,
                    MappingJackson2HttpMessageConverter.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String fastjson(Object value, FilterSession session) {
        return com.alibaba.fastjson.JSON.toJSONString(new FastjsonResponseFilterEngine().apply(value, session, null));
    }

    static String fastjsonNoRef(Object value, FilterSession session) {
        return com.alibaba.fastjson.JSON.toJSONString(new FastjsonResponseFilterEngine().apply(value, session, null),
                SerializerFeature.DisableCircularReferenceDetect, SerializerFeature.WriteMapNullValue);
    }

    static String fastjson2(Object value, FilterSession session) {
        return com.alibaba.fastjson2.JSON.toJSONString(new Fastjson2ResponseFilterEngine().apply(value, session, null));
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
