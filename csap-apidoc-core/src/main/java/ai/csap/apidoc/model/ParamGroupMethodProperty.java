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
