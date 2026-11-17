package ai.csap.validation.strategy;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ai.csap.validation.factory.Validate;
import ai.csap.validation.type.ValidatePatternType;
import ai.csap.validation.type.ValidationStrategyType;


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
    private static final Pattern INVALID = Pattern.compile("");
    private static final Logger LOG = LoggerFactory.getLogger(PatternValidationStrategy.class);

    /**
     * 处理正则验证
     *
     * @param validatorField 验证字段
     * @param value          数据
     * @return 结果
     */
    private boolean pattern(Validate.ConstraintValidatorField validatorField, Object value) {
        String regex = validatorField.getPattern();
        if (regex == null || regex.isEmpty()) {
            return true;
        }
        Pattern pattern = PATTERN_MAP.computeIfAbsent(regex, PatternValidationStrategy::compile);
        if (pattern == INVALID) {
            return true;
        }
        return pattern.matcher(value.toString()).matches();
    }

    /**
     * 编译正则;写的是内置正则的枚举名(如 PATTERN_35)时取对应正则.
     * 正则写错时记一次 warn 并跳过该规则,不影响请求。
     */
    private static Pattern compile(String regex) {
        String actual = regex;
        if (regex.startsWith("PATTERN_")) {
            try {
                actual = ValidatePatternType.valueOf(regex).getPattern();
            } catch (IllegalArgumentException ignored) {
                // 不是内置正则名,按正则处理
            }
        }
        try {
            return Pattern.compile(actual);
        } catch (PatternSyntaxException e) {
            LOG.warn("invalid validate pattern '{}', the rule is skipped: {}", regex, e.getDescription());
            return INVALID;
        }
    }

    @Override
    public ValidationStrategyType validationType() {
        return ValidationStrategyType.Pattern;
    }

    @Override
    public boolean validation(Object value, Validate.ConstraintValidatorField validatorField) {
        if (Objects.isNull(value)) {
            return false;
        }
        return !pattern(validatorField, value);
    }
}
