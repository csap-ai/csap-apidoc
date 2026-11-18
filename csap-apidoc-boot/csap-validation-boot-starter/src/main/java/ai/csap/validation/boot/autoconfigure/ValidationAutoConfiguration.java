package ai.csap.validation.boot.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import ai.csap.apidoc.core.ApiMethodHandle;
import ai.csap.validation.ValidateIntercept;
import ai.csap.validation.advice.ValidateExceptionHandler;
import ai.csap.validation.factory.DefaultValidateFactoryImpl;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.properties.ValidationProperties;
import ai.csap.validation.runtime.LoggingViolationRecorder;
import ai.csap.validation.runtime.MicrometerViolationRecorder;
import ai.csap.validation.runtime.ValidationExecutor;
import ai.csap.validation.runtime.ViolationRecorder;
import ai.csap.validation.strategy.AnnotationValidationStrategy;
import ai.csap.validation.strategy.NotEmptyValidationStrategy;
import ai.csap.validation.strategy.NotNullValidationStrategy;
import ai.csap.validation.strategy.PatternValidationStrategy;
import ai.csap.validation.strategy.context.ValidationStrategyContext;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Validate config.
 *
 * <p>规则工厂、异常处理器、校验策略总是注册(生成文档需要规则工厂);
 * 运行时校验(包装参数解析器)默认关闭,{@code csap.validation.mode} 或按接口覆盖不是 off 时才注册,
 * 见 {@link OnValidationActiveCondition}。
 * <p>Created on 2021/1/6
 *
 * @author yangchengfu
 * @since 1.0
 */
@Configuration(proxyBeanMethods = false)
public class ValidationAutoConfiguration {
    @ConfigurationProperties(prefix = ValidationProperties.PREFIX)
    @Bean
    @ConditionalOnMissingBean
    public ValidationProperties validationProperties() {
        return new ValidationProperties();
    }

    @ConditionalOnMissingBean
    @Bean
    public ValidateExceptionHandler validateExceptionHandler(ValidationProperties validationProperties) {
        return new ValidateExceptionHandler(validationProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public IValidateFactory validateFactory(ValidationProperties validationProperties) {
        return new DefaultValidateFactoryImpl(ConstraintHelper.forAllBuiltinConstraints(), validationProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ValidationStrategyContext validationStrategyContext() {
        return new ValidationStrategyContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public NotNullValidationStrategy notNullValidationStrategy() {
        return new NotNullValidationStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public NotEmptyValidationStrategy notEmptyValidationStrategy() {
        return new NotEmptyValidationStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public PatternValidationStrategy patternValidationStrategy() {
        return new PatternValidationStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    public AnnotationValidationStrategy annotationValidationStrategy() {
        return new AnnotationValidationStrategy();
    }

    /**
     * Spring Boot 3:jakarta.validation.ConstraintViolationException
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.validation.ConstraintViolationException")
    static class JakartaConstraintViolationConfig {
        @Bean
        @ConditionalOnMissingBean
        JakartaConstraintViolationExceptionHandler jakartaConstraintViolationExceptionHandler(
                ValidationProperties validationProperties) {
            return new JakartaConstraintViolationExceptionHandler(validationProperties);
        }
    }

    /**
     * Spring Boot 2:javax.validation.ConstraintViolationException
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "javax.validation.ConstraintViolationException")
    static class JavaxConstraintViolationConfig {
        @Bean
        @ConditionalOnMissingBean
        JavaxConstraintViolationExceptionHandler javaxConstraintViolationExceptionHandler(
                ValidationProperties validationProperties) {
            return new JavaxConstraintViolationExceptionHandler(validationProperties);
        }
    }

    /**
     * 运行时校验:mode / enabled / endpoints 使任一接口不是 off 时注册
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(OnValidationActiveCondition.class)
    public static class AutoValidateConfig {

        @Bean
        @ConditionalOnMissingBean
        public ValidateIntercept validateIntercept(IValidateFactory validateFactory) {
            return new ValidateIntercept(validateFactory);
        }

        @Bean
        @ConditionalOnMissingBean(name = "csapLoggingViolationRecorder")
        public LoggingViolationRecorder csapLoggingViolationRecorder() {
            return new LoggingViolationRecorder();
        }

        @Bean
        @ConditionalOnMissingBean
        public ValidationExecutor validationExecutor(ValidationProperties validationProperties,
                                                     ObjectProvider<ApiMethodHandle> handles,
                                                     ObjectProvider<ViolationRecorder> recorders) {
            return new ValidationExecutor(validationProperties,
                    () -> handles.orderedStream().collect(Collectors.toList()),
                    () -> new ArrayList<>(recorders.orderedStream().collect(Collectors.toList())));
        }

        @Bean
        @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
        @ConditionalOnClass(RequestMappingHandlerAdapter.class)
        public static ValidatingHandlerAdapterPostProcessor csapValidatingHandlerAdapterPostProcessor(
                ObjectProvider<ValidationExecutor> executor) {
            return new ValidatingHandlerAdapterPostProcessor(executor);
        }
    }

    /**
     * classpath 中有 Micrometer 且存在 MeterRegistry 时计数
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(OnValidationActiveCondition.class)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class MicrometerRecorderConfig {
        @Bean
        @ConditionalOnMissingBean(MicrometerViolationRecorder.class)
        ViolationRecorder csapMicrometerViolationRecorder(ObjectProvider<MeterRegistry> registry) {
            return new LazyMicrometerViolationRecorder(registry);
        }
    }

    /**
     * MeterRegistry 由 actuator 自动配置创建,这里在首次记录时再取,避免自动配置顺序问题
     */
    static final class LazyMicrometerViolationRecorder implements ViolationRecorder {
        private final ObjectProvider<MeterRegistry> registry;
        private volatile List<MicrometerViolationRecorder> delegate;

        LazyMicrometerViolationRecorder(ObjectProvider<MeterRegistry> registry) {
            this.registry = registry;
        }

        @Override
        public void record(ai.csap.validation.runtime.ValidationViolation violation) {
            List<MicrometerViolationRecorder> current = delegate;
            if (current == null) {
                MeterRegistry meterRegistry = registry.getIfAvailable();
                current = meterRegistry == null ? List.of() : List.of(new MicrometerViolationRecorder(meterRegistry));
                delegate = current;
            }
            current.forEach(r -> r.record(violation));
        }
    }
}
