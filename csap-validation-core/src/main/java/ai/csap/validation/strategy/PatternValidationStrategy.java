package ai.csap.validation.strategy;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import ai.csap.validation.factory.Validate;
import ai.csap.validation.type.ValidationStrategyType;

import jakarta.validation.ConstraintValidatorContext;

/**
 * 正则表达式策略实现
 *
 * @Author ycf
 * @Date 2021/10/11 2:51 下午
 * @Version 1.0
 */
@Component
public class PatternValidationStrategy implements ValidationStrategy<Object> {
    /**
     * 正则全局
     */
    private static final Map<String, Pattern> PATTERN_MAP = new ConcurrentHashMap<>(16);

    /**
     * 处理正则验证
     *
     * @param validatorField 验证字段
     * @param value          数据
     * @return 结果
     */
    private boolean pattern(Validate.ConstraintValidatorField validatorField, Object value) {
        return PATTERN_MAP.computeIfAbsent(validatorField.getPattern(), i -> Pattern.compile(validatorField.getPattern()))
                .matcher(value.toString()).matches();
    }

    @Override
    public ValidationStrategyType validationType() {
        return ValidationStrategyType.Pattern;
    }

    @Override
    public boolean validation(Object value, Validate.ConstraintValidatorField validatorField, ConstraintValidatorContext validatorContext) {
        if (Objects.isNull(value)) {
            return false;
        }
        return !pattern(validatorField, value);
    }
}
