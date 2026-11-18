package ai.csap.apidoc.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.validation.factory.Validate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * yaml 属性
 *
 * @Author ycf
 * @Date 2021/11/10 2:56 下午
 * @Version 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParamGroupMethodProperty {
    /**
     * 请求参数
     * key is field name value is field property
     */
    private Map<String, ParamDataValidate> request = new HashMap<>(16);
    /**
     * 返回参数
     * key is field name value is field property
     */
    private Map<String, ParamDataValidate> response = new HashMap<>(16);
    /**
     * 接口状态覆盖(开发中 / 完成 / 过时):不为空时优先于注解上的状态;为空表示没有覆盖。
     * 与 request、response 一起存取(YAML、数据库、Redis、SQLite),旧版本读取时忽略。
     *
     * @since 1.4.0
     */
    private ApiStatus status;
    /**
     * 状态备注,如替代接口、下线时间
     *
     * @since 1.4.0
     */
    private String statusNote;

    public static Map<String, ParamDataValidate> sortedKeyLength(Map<String, ParamDataValidate> data) {
        return data
                .entrySet()
                .stream()
                .sorted(Comparator.comparingInt(i -> i.getKey().length()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (old1, new1) -> old1, LinkedHashMap::new));
    }

    /**
     * 参数验证实体类
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ParamDataValidate {
        /**
         * 是否必传
         */
        private Boolean required = Boolean.FALSE;
        /**
         * 是否包含
         */
        private Boolean include;
        /**
         * 参数类型
         */
        private ParamType paramType;
        /**
         * 参数备注
         */
        private String description;
        /**
         * 验证信息
         */
        private List<Validate.ConstraintValidatorField> validate;
        /**
         * 字段示例,文档里的示例值优先用它
         *
         * @since 1.4.0
         */
        private String example;
        /**
         * 套用字段模板时记下的模板名,只用于展示和“与模板不一致”的提示,运行时不读
         *
         * @since 1.4.0
         */
        private String template;
        /**
         * Mock 取值:任意 JSON 值(字面值,或 @int(lo,hi)、@oneOf(...)、@uuid 之类的表达式字符串);
         * 这里只原样存取并透传到文档的 Parameter,不解释
         *
         * @since 1.4.0
         */
        private Object mock;

        public ParamDataValidate(Boolean required, Boolean include, ParamType paramType, String description,
                                 List<Validate.ConstraintValidatorField> validate) {
            this.required = required;
            this.include = include;
            this.paramType = paramType;
            this.description = description;
            this.validate = validate;
        }

        /**
         * 复制一份:校验列表是新列表(元素共用),其余字段原样带上。
         * 返回给文档生成的规则必须是副本,否则往里追加注解校验器会污染缓存。
         */
        public ParamDataValidate copyForUse() {
            ParamDataValidate copy = new ParamDataValidate(required, include, paramType, description, null);
            copy.example = example;
            copy.template = template;
            copy.mock = mock;
            copy.getValidate().addAll(getValidate());
            return copy;
        }

        public List<Validate.ConstraintValidatorField> getValidate() {
            if (Objects.isNull(validate)) {
                validate = Collections.synchronizedList(Lists.newArrayList());
            }
            return validate;
        }

        public Boolean getRequired() {
            return Objects.isNull(required) ? Boolean.FALSE : required;
        }
    }
}
