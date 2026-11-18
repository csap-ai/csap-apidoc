package ai.csap.apidoc.strategy.store;

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
public final class RuleJson {

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

    public static String write(ParamGroupMethodProperty property) {
        try {
            return MAPPER.writeValueAsString(property);
        } catch (IOException e) {
            throw new IllegalStateException("规则序列化失败: " + e.getMessage(), e);
        }
    }

    public static ParamGroupMethodProperty read(String json) throws IOException {
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

    private static final ObjectMapper TREE = new ObjectMapper();

    /**
     * 比较两份规则 JSON 是否相同:先比字符串,再按 JSON 结构比较(忽略空白和键顺序,别的语言写入的格式可能不同)。
     */
    public static boolean sameJson(String a, String b) {
        if (java.util.Objects.equals(a, b)) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        try {
            return TREE.readTree(a).equals(TREE.readTree(b));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 深拷贝(经 JSON 往返;注解校验器不在其中)
     */
    public static ParamGroupMethodProperty copy(ParamGroupMethodProperty source) {
        try {
            return read(write(source));
        } catch (IOException e) {
            throw new IllegalStateException("规则复制失败: " + e.getMessage(), e);
        }
    }

    /**
     * 规则是否为空:没有请求 / 返回字段规则,也没有状态覆盖(只改了状态的接口不是空规则)
     */
    public static boolean isEmpty(ParamGroupMethodProperty p) {
        return p == null
                || ((p.getRequest() == null || p.getRequest().isEmpty())
                && (p.getResponse() == null || p.getResponse().isEmpty())
                && p.getStatus() == null
                && (p.getStatusNote() == null || p.getStatusNote().isEmpty()));
    }
}
