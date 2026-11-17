package ai.csap.validation.runtime;

import ai.csap.validation.properties.ValidationMode;

/**
 * 一次校验失败(不含参数值).
 *
 * @author ycf
 * @since 1.2.0
 */
public final class ValidationViolation {
    private final String endpoint;
    private final String field;
    private final String rule;
    private final String code;
    private final String message;
    private final ValidationMode mode;

    public ValidationViolation(String endpoint, String field, String rule, String code, String message,
                               ValidationMode mode) {
        this.endpoint = endpoint;
        this.field = field;
        this.rule = rule;
        this.code = code;
        this.message = message;
        this.mode = mode;
    }

    /**
     * @return 接口键:Controller 全限定类名.方法名
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * @return 规则对应的字段
     */
    public String getField() {
        return field;
    }

    /**
     * @return 失败的规则:NotNull / NotEmpty / Pattern / 注解简单类名
     */
    public String getRule() {
        return rule;
    }

    public String getCode() {
        return code;
    }

    /**
     * @return 已按 tipType 格式化的提示
     */
    public String getMessage() {
        return message;
    }

    public ValidationMode getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return "ValidationViolation{endpoint=" + endpoint + ", field=" + field + ", rule=" + rule
                + ", code=" + code + ", message=" + message + ", mode=" + mode + '}';
    }
}
