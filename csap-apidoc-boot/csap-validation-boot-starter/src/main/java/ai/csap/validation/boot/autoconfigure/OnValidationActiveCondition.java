package ai.csap.validation.boot.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import ai.csap.validation.properties.ValidationProperties;

/**
 * 运行时校验是否启用:全局模式(含旧开关 enabled)或任一按接口覆盖不是 off.
 *
 * @author ycf
 * @since 1.2.0
 */
class OnValidationActiveCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ValidationProperties properties = Binder.get(context.getEnvironment())
                .bind(ValidationProperties.PREFIX, ValidationProperties.class)
                .orElseGet(ValidationProperties::new);
        if (properties.isActive()) {
            return ConditionOutcome.match("csap.validation mode is " + properties.effectiveMode()
                    + ", endpoints " + properties.getEndpoints());
        }
        return ConditionOutcome.noMatch("csap.validation is off (mode / enabled / endpoints)");
    }
}
