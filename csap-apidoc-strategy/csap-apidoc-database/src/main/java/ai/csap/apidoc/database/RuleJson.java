package ai.csap.apidoc.database;

import java.io.IOException;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import ai.csap.apidoc.model.ParamGroupMethodProperty;

/**
 * 规则 JSON 的编解码(Jackson):尊重 {@code @JsonIgnore},忽略 null,忽略未知字段和未知枚举值。
 * 键按字典序输出,同一份规则每次序列化的结果一致。
 *
 * @author ycf
 * @since 1.4.0
 */
final class RuleJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
            .addMixIn(ParamGroupMethodProperty.ParamDataValidate.class, ParamDataValidateMixin.class);

    private RuleJson() {
    }

    /**
     * 空的 validate 列表不输出
     */
    @SuppressWarnings("unused")
    abstract static class ParamDataValidateMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public abstract java.util.List<ai.csap.validation.factory.Validate.ConstraintValidatorField> getValidate();
    }

    static String write(ParamGroupMethodProperty property) {
        try {
            return MAPPER.writeValueAsString(property);
        } catch (IOException e) {
            throw new IllegalStateException("规则序列化失败: " + e.getMessage(), e);
        }
    }

    static ParamGroupMethodProperty read(String json) throws IOException {
        ParamGroupMethodProperty p = MAPPER.readValue(json, ParamGroupMethodProperty.class);
        if (p == null) {
            p = new ParamGroupMethodProperty();
        }
        if (p.getRequest() == null) {
            p.setRequest(new TreeMap<>());
        }
        if (p.getResponse() == null) {
            p.setResponse(new TreeMap<>());
        }
        return p;
    }

    /**
     * 深拷贝(经 JSON 往返;注解校验器不在其中)
     */
    static ParamGroupMethodProperty copy(ParamGroupMethodProperty source) {
        try {
            return read(write(source));
        } catch (IOException e) {
            throw new IllegalStateException("规则复制失败: " + e.getMessage(), e);
        }
    }

    static boolean isEmpty(ParamGroupMethodProperty p) {
        return p == null
                || ((p.getRequest() == null || p.getRequest().isEmpty())
                && (p.getResponse() == null || p.getResponse().isEmpty()));
    }
}
