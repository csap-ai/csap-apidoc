package ai.csap.validation.strategy;

import org.springframework.stereotype.Component;

import ai.csap.validation.factory.Validate;
import ai.csap.validation.type.ValidationStrategyType;

import jakarta.validation.ConstraintValidatorContext;

/**
 * 注解方式验证策略实现
 *
 * @Author ycf
 * @Date 2021/10/11 2:51 下午
 * @Version 1.0
 */
@Component
public class AnnotationValidationStrategy implements ValidationStrategy<Object> {
    @Override
    public ValidationStrategyType validationType() {
        return ValidationStrategyType.Annotation;
    }

    @Override
    public boolean validation(Object value, Validate.ConstraintValidatorField validatorField, ConstraintValidatorContext validatorContext) {
        return !validatorField.getValidator().isValid(value, validatorContext);
    }
}
