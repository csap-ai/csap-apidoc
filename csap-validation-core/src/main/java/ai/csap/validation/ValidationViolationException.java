package ai.csap.validation;

import ai.csap.apidoc.util.ValidateException;

/**
 * CSAP 规则校验失败.
 *
 * <p>是 {@link ValidateException} 的子类,返回格式与 1.1.x 相同({@code code} + {@code message}),
 * 额外带上失败的字段和规则,供观察模式记录日志和指标。
 *
 * @author ycf
 * @since 1.2.0
 */
public class ValidationViolationException extends ValidateException {
    private static final long serialVersionUID = 1L;

    /**
     * 规则对应的字段键
     */
    private final String field;
    /**
     * 失败的规则:NotNull / NotEmpty / Pattern / 注解简单类名
     */
    private final String rule;

    public ValidationViolationException(String code, String message, String field, String rule) {
        super(code, message);
        this.field = field;
        this.rule = rule;
    }

    public String getField() {
        return field;
    }

    public String getRule() {
        return rule;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // 参数校验失败是常规的客户端错误,不需要堆栈
        return this;
    }
}
