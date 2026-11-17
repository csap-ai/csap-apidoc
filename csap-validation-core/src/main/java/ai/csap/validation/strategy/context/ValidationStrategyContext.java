package ai.csap.validation.strategy.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import ai.csap.validation.factory.Validate;
import ai.csap.validation.strategy.AnnotationValidationStrategy;
import ai.csap.validation.strategy.NotEmptyValidationStrategy;
import ai.csap.validation.strategy.NotNullValidationStrategy;
import ai.csap.validation.strategy.PatternValidationStrategy;
import ai.csap.validation.strategy.ValidationStrategy;
import ai.csap.validation.type.ValidationStrategyType;

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

    static {
        // 内置策略不依赖 Spring 容器;容器中的同类型策略 Bean 会在启动后覆盖它们
        register(new NotNullValidationStrategy());
        register(new NotEmptyValidationStrategy());
        register(new PatternValidationStrategy());
        register(new AnnotationValidationStrategy());
    }

    /**
     * 注册(或覆盖)一种校验策略
     *
     * @param strategy 策略
     */
    @SuppressWarnings("unchecked")
    public static void register(ValidationStrategy<?> strategy) {
        VALIDATION_STRATEGY_MAP.put(strategy.validationType(), (ValidationStrategy<Object>) strategy);
    }

    /**
     * Validate data with specified strategy
     *
     * @param validatorField validator field configuration
     * @param value          data to validate
     * @return true 表示规则不通过
     */
    public static boolean validate(Validate.ConstraintValidatorField validatorField, Object value) {
        ValidationStrategy<Object> strategy = validatorField.getType() == null ? null
                : VALIDATION_STRATEGY_MAP.get(validatorField.getType());
        if (strategy == null) {
            log.warn("no validation strategy for rule type {}, the rule is skipped", validatorField.getType());
            return false;
        }
        return strategy.validation(value, validatorField);
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
                .forEach((k, v) -> register(v));
        log.info("Loaded {} ValidationStrategy bean(s) after singletons instantiation", VALIDATION_STRATEGY_MAP.size());
    }
}
