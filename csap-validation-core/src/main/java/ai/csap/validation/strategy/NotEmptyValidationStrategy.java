package ai.csap.validation.strategy;

import org.springframework.stereotype.Component;

import ai.csap.validation.factory.Validate;
import ai.csap.validation.type.ValidationStrategyType;

import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 不能为空字符 策略实现
 *
 * @Author ycf
 * @Date 2021/10/11 2:51 下午
 * @Version 1.0
 */
@Component
public class NotEmptyValidationStrategy implements ValidationStrategy<Object> {
    @Override
    public ValidationStrategyType validationType() {
        return ValidationStrategyType.NotEmpty;
    }

    @Override
    public boolean validation(Object value, Validate.ConstraintValidatorField validatorField, ConstraintValidatorContext validatorContext) {
        return ObjectUtil.isEmpty(value);
    }
}
