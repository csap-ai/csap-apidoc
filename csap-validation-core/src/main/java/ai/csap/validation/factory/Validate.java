package ai.csap.validation.factory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import ai.csap.apidoc.type.ModelType;
import ai.csap.apidoc.util.ValidateException;
import ai.csap.validation.strategy.context.ValidationStrategyContext;
import ai.csap.validation.type.ValidationStrategyType;
import ai.csap.validation.type.ValidationTipType;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 验证实体类.
 * <p>Created on 2021/1/22
 *
 * @author yangchengfu
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Validate {
    private static final String FIEID_TIP = "字段%s,%s";
    /**
     * 返回值 过滤参数
     */
    private List<FilterClassParam> filterClassParams = new ArrayList<>();
    /**
     * 字段验证
     * <p>
     * key is fileName ?.fileName
     * value is validate value list
     * <p/>
     * 一级键名称为 controllerName.method.model.field
     * 二级级键名称为 controllerName.method.model.field.field
     * 多级键名称以此类推
     */
    private Map<String, ValidateField> validateFieldMap = new ConcurrentHashMap<>(16);


    /**
     * 根据字段名称 或者字段验证列表
     *
     * @param fieldKey 字段标识
     * @return 返回字段验证列表
     */
    public ValidateField field(String fieldKey) {
        return validateFieldMap.get(fieldKey);
    }

    /**
     * 添加验证的字段
     *
     * @param fieldKey             字段标识
     * @param fieldRemark          字段描述
     * @param constraintValidators 验证逻辑
     * @return 字段验证
     */
    public ValidateField addValidateField(String fieldKey, String fieldRemark,
                                          List<ConstraintValidatorField> constraintValidators) {
        return validateFieldMap.computeIfAbsent(fieldKey, k -> new ValidateField()
                .constraintValidators(constraintValidators).remark(fieldRemark).fieldName(fieldKey));
    }

    /**
     * 添加验证的字段
     *
     * @param fieldKey             字段标识
     * @param constraintValidators 验证逻辑
     * @return 字段验证
     */
    public ValidateField addValidateField(String fieldKey, ValidateField constraintValidators) {
        return validateFieldMap.computeIfAbsent(fieldKey, k -> constraintValidators);
    }

    /**
     * 添加验证的字段
     *
     * @param fieldKey 字段标识
     * @return 字段验证
     */
    public ValidateField addValidateField(String fieldKey) {
        return validateFieldMap.computeIfAbsent(fieldKey, k -> new ValidateField());
    }

    /**
     * 添加验证的字段
     *
     * @param fieldKey 字段标识
     * @return 字段验证
     */
    public ValidateField addValidateField(String fieldKey, BaseModel baseModel) {
        return validateFieldMap.computeIfAbsent(fieldKey, k -> new ValidateField().modelType(baseModel.getModelType()));
    }

    /**
     * 添加验证的字段
     *
     * @param fieldKey  字段标识
     * @param baseModel 类型对象
     * @return 字段验证
     */
    public ValidateField putValidateField(String fieldKey, BaseModel baseModel) {
        return validateFieldMap.put(fieldKey, new ValidateField().modelType(baseModel.getModelType()));
    }

    /**
     * 过滤返回参数
     *
     * @param modelClass 参数对象
     * @return 返回过滤属性
     */
    public FilterClassParam filterResponse(Class<?> modelClass) {
        return filterClassParams.stream()
                                .filter(i -> modelClass.equals(i.getType()))
                                .findFirst()
                                .orElseGet(() -> {
                                    FilterClassParam filterClassParam = new FilterClassParam();
                                    filterClassParam.type(modelClass);
                                    filterClassParams.add(filterClassParam);
                                    return filterClassParam;
                                });
    }

    /**
     * 清空属性
     */
    public void clear() {
        filterClassParams.clear();
        validateFieldMap.clear();
    }

    /**
     * 字段验证
     */
    @Data
    public static class ValidateField {
        /**
         * 验证逻辑
         */
        private List<ConstraintValidatorField> constraintValidators;
        /**
         * 子级验证信息
         */
        private Map<String, ValidateField> children = new LinkedHashMap<>();
        /**
         * 当前字段名称
         */
        private String fieldName;
        /**
         * 字段描述
         */
        private String remark;
        /**
         * 对象类型
         */
        private ModelType modelType;

        public ValidateField modelType(ModelType modelType) {
            this.modelType = modelType;
            return this;
        }

        public ValidateField remark(String remark) {
            this.remark = remark;
            return this;
        }

        public String getRemark() {
            return StrUtil.isEmpty(remark) ? fieldName : remark;
        }

        public ValidateField fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public ValidateField constraintValidators(List<ConstraintValidatorField> constraintValidators) {
            this.constraintValidators = constraintValidators;
            return this;
        }

        /**
         * 添加验证的字段
         *
         * @param fieldKey             字段标识
         * @param fieldRemark          字段描述
         * @param constraintValidators 验证逻辑
         * @return 验证对象
         */
        public ValidateField addValidateField(String fieldKey, String fieldRemark,
                                              List<ConstraintValidatorField> constraintValidators,
                                              BaseModel baseModel) {
            return children.computeIfAbsent(fieldKey, k -> new ValidateField()
                    .modelType(baseModel.getModelType())
                    .constraintValidators(constraintValidators)
                    .remark(fieldRemark)
                    .fieldName(fieldKey));
        }

        /**
         * 添加验证的字段
         *
         * @param fieldKey             字段标识
         * @param fieldRemark          字段描述
         * @param constraintValidators 验证逻辑
         * @return 字段验证
         */
        public ValidateField putValidateField(String fieldKey, String fieldRemark,
                                              List<ConstraintValidatorField> constraintValidators,
                                              BaseModel baseModel) {
            ValidateField validateField = new ValidateField()
                    .modelType(baseModel.getModelType())
                    .constraintValidators(constraintValidators)
                    .fieldName(fieldKey)
                    .remark(fieldRemark);
            children.put(fieldKey, validateField);
            return validateField;
        }

        /**
         * 添加验证的字段
         *
         * @param fieldKey      字段标识
         * @param validateField 验证参数
         * @return 字段验证
         */
        public ValidateField addValidateField(String fieldKey, ValidateField validateField) {
            return children.computeIfAbsent(fieldKey, k -> validateField);
        }

        /**
         * 验证当前字段
         *
         * @param value   需要验证的数据
         * @param tipType 提示类型
         * @return 字段验证
         */
        public ValidateField validators(Object value, ValidationTipType tipType,
                                        ConstraintValidatorContext validatorContext) {
            return validators(value, tipType, getConstraintValidators(), validatorContext);
        }

        /**
         * 验证当前字段
         *
         * @param value                   需要验证的数据
         * @param constraintValidatorList 需要验证的业务逻辑
         * @return 字段验证
         */
        public ValidateField validators(Object value, ValidationTipType tipType,
                                        List<ConstraintValidatorField> constraintValidatorList,
                                        ConstraintValidatorContext validatorContext) {
            if (CollectionUtil.isNotEmpty(constraintValidatorList)) {
                constraintValidatorList.stream()
                                       .filter(i -> ValidationStrategyContext.validate(i, value, validatorContext))
                                       .findFirst().ifPresent(i -> {
                                           throw new ValidateException(i.getCode(), (Objects.nonNull(i.getTipType()) ? i.getTipType() : tipType).validateMessage(this, i.getMessage()));
                                       });
            }
            return this;
        }

        /**
         * 验证当前级联业务
         */
        public ValidateField validatorsChildren(Function<String, Object> function) {
            children.entrySet().stream().map(e -> {
                Object o = function.apply(e.getKey());
                return e;
            });
            return null;
        }

    }

    /**
     * 字段验证详细信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Accessors(chain = true)
    public static class ConstraintValidatorField {
        /**
         * 描述
         */
        private String descr;
        /**
         * 验证的优先级 数值越小优先级越高 默认为1
         */
        private Integer level = 1;
        /**
         * 验证的类型
         */
        private ValidationStrategyType type;
        /**
         * 内置验证逻辑
         */
        private ConstraintValidator<? extends Annotation, Object> validator;
        /**
         * 当前注解
         */
        private Annotation annotation;
        /**
         * 编码
         */
        private String code;
        /**
         * 提示消息
         */
        private String message;
        /**
         * 验证规则
         */
        private String pattern;
        /**
         * 提示类型
         */
        private ValidationTipType tipType;
        /**
         * 自定义属性
         */
        private Map<String, Object> attributes;
    }
}
