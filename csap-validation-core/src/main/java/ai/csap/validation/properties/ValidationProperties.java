package ai.csap.validation.properties;

import java.util.LinkedHashMap;
import java.util.Map;

import ai.csap.validation.type.ValidationTipType;

import lombok.Data;

/**
 * 验证配置.
 * <p>Created on 2020/1/6
 *
 * @author yangchengfu
 * @since 1.0
 */
@Data
public class ValidationProperties {
    public static final String PREFIX = "csap.validation";
    /**
     * 只配了必填、没配校验规则时的默认错误码(与 Go 版一致)
     */
    public static final String DEFAULT_REQUIRED_CODE = "400";
    /**
     * 只配了必填、没配校验规则时的默认提示(与 Go 版一致)
     */
    public static final String DEFAULT_REQUIRED_MESSAGE = "不能为空";
    /**
     * 错误码的名称
     */
    private String code = "code";
    /**
     * 错误描述的名称
     */
    private String message = "message";

    /**
     * 旧开关(1.1.x):只写 enabled=true 且没写 mode 时等同 mode=enforce;
     * enabled=false 时全部关闭(包括按接口覆盖)。
     */
    private Boolean enabled;

    /**
     * 全局校验模式:off(默认)/ observe / enforce
     */
    private ValidationMode mode;

    /**
     * 校验失败时响应的 HTTP 状态码,默认 200(响应体始终是 {code, message})
     */
    private int httpStatus = 200;

    /**
     * 按接口覆盖模式,键为 Controller 全限定类名.方法名,如 com.x.UserController.create
     */
    private Map<String, ValidationMode> endpoints = new LinkedHashMap<>();

    /**
     * 非必填字段上的规则:validate(默认)没传值(null)时不校验、传了值就校验,与 Go 版一致;
     * skip 恢复 1.3.0 及以前的行为,非必填字段一律不校验。
     *
     * @since 1.4.0
     */
    private OptionalFieldRules optionalFieldRules = OptionalFieldRules.VALIDATE;

    /**
     * 验证提示的类型
     */
    private ValidationTipType tipType = ValidationTipType.TIP_1;

    /**
     * 字段只配了必填、没配任何校验规则时,值为空(null、空字符串、空集合)返回的错误码
     */
    private String requiredCode = DEFAULT_REQUIRED_CODE;

    /**
     * 字段只配了必填、没配任何校验规则时,值为空返回的提示(按 tip-type 拼上字段名)
     */
    private String requiredMessage = DEFAULT_REQUIRED_MESSAGE;

    /**
     * 全局生效的模式(已合并旧开关 enabled)
     *
     * @return 模式
     */
    public ValidationMode effectiveMode() {
        if (Boolean.FALSE.equals(enabled)) {
            return ValidationMode.OFF;
        }
        if (mode != null) {
            return mode;
        }
        return Boolean.TRUE.equals(enabled) ? ValidationMode.ENFORCE : ValidationMode.OFF;
    }

    /**
     * 某个接口生效的模式
     *
     * @param endpoint Controller 全限定类名.方法名
     * @return 模式
     */
    public ValidationMode modeFor(String endpoint) {
        if (Boolean.FALSE.equals(enabled)) {
            return ValidationMode.OFF;
        }
        ValidationMode override = endpoints == null || endpoint == null ? null : endpoints.get(endpoint);
        return override != null ? override : effectiveMode();
    }

    /**
     * 是否有任何接口需要校验(全局或按接口覆盖不是 off)
     *
     * @return 是否启用运行时校验
     */
    public boolean isActive() {
        if (Boolean.FALSE.equals(enabled)) {
            return false;
        }
        if (effectiveMode() != ValidationMode.OFF) {
            return true;
        }
        return endpoints != null && endpoints.values().stream().anyMatch(m -> m != null && m != ValidationMode.OFF);
    }
}
