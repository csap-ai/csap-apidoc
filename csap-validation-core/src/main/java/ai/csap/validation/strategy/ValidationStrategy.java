package ai.csap.validation.strategy;

import ai.csap.validation.factory.Validate;
import ai.csap.validation.type.ValidationStrategyType;

import jakarta.validation.ConstraintValidatorContext;

/**
 * 验证策略
 *
 * @Author ycf
 * @Date 2021/10/9 3:16 下午
 * @Version 1.0
 */
public interface ValidationStrategy<T> {
    /**
     * 验证类型
     *
     * @return 验证策略
     */
    ValidationStrategyType validationType();

    /**
     * 验证
     *
     * @param value            验证的数据
     * @param validatorField   验证信息
     * @param validatorContext 验证上下文
     * @return 是否匹配
     */
    boolean validation(T value, Validate.ConstraintValidatorField validatorField, ConstraintValidatorContext validatorContext);
}
