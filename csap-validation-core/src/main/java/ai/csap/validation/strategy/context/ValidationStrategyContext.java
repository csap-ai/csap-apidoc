package ai.csap.validation.strategy.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import ai.csap.validation.factory.Validate;
import ai.csap.validation.strategy.ValidationStrategy;
import ai.csap.validation.type.ValidationStrategyType;

import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Validation Strategy Context
 *
 * <p>Uses {@link SmartInitializingSingleton} to ensure all ValidationStrategy beans
 * are loaded after all singletons are initialized, including lazy-loaded beans.
 *
 * @Author ycf
 * @Date 2021/10/11 3:00 下午
 * @Version 1.0
 */
@Slf4j
@Component
public class ValidationStrategyContext implements ApplicationContextAware, SmartInitializingSingleton {

    /**
     * ApplicationContext for loading beans
     */
    private ApplicationContext applicationContext;

    /**
     * Validation strategy map (thread-safe)
     */
    private static final Map<ValidationStrategyType, ValidationStrategy<Object>> VALIDATION_STRATEGY_MAP = new ConcurrentHashMap<>();

    /**
     * Validate data with specified strategy
     *
     * @param validatorField validator field configuration
     * @param value          data to validate
     * @param validatorContext validation context
     * @return validation result
     */
    public static Boolean validate(Validate.ConstraintValidatorField validatorField, Object value, ConstraintValidatorContext validatorContext) {
        return VALIDATION_STRATEGY_MAP.get(validatorField.getType()).validation(value, validatorField, validatorContext);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Called after all singleton beans are fully initialized.
     * Loads all ValidationStrategy beans at the optimal time.
     *
     * @see SmartInitializingSingleton
     */
    @Override
    @SuppressWarnings("unchecked")
    public void afterSingletonsInstantiated() {
        applicationContext.getBeansOfType(ValidationStrategy.class)
                .forEach((k, v) -> VALIDATION_STRATEGY_MAP.put(v.validationType(), (ValidationStrategy<Object>) v));
        log.info("Loaded {} ValidationStrategy bean(s) after singletons instantiation", VALIDATION_STRATEGY_MAP.size());
    }
}
