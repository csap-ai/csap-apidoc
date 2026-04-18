package ai.csap.validation.boot.autoconfigure;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import ai.csap.validation.ValidateIntercept;
import ai.csap.validation.advice.ValidateExceptionHandler;
import ai.csap.validation.factory.DefaultValidateFactoryImpl;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.properties.ValidationProperties;

/**
 * Validate config.
 * <p>Created on 2021/1/6
 *
 * @author yangchengfu
 * @since 1.0
 */
@Configuration
@ComponentScan("ai.csap.validation")
public class ValidationAutoConfiguration {
    @ConfigurationProperties(prefix = ValidationProperties.PREFIX)
    @Bean
    public ValidationProperties validationProperties() {
        return new ValidationProperties();
    }

    @ConditionalOnMissingBean
    @Bean
    public ValidateExceptionHandler validateExceptionHandler() {
        return new ValidateExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public IValidateFactory validateFactory(ValidationProperties validationProperties) {
        return new DefaultValidateFactoryImpl(ConstraintHelper.forAllBuiltinConstraints(), validationProperties);
    }

    /**
     * 配置
     */
    @Configuration
    @ConditionalOnProperty(prefix = ValidationProperties.PREFIX, name = "enabled",
            havingValue = "true")
    public static class AutoValidateConfig {

        @Bean
        @ConditionalOnMissingBean
        public ValidateIntercept validateIntercept(IValidateFactory validateFactory) {
            return new ValidateIntercept(validateFactory);
        }

    }
}
