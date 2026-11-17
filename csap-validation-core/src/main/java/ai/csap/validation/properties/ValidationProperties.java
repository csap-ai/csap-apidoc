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
     * 验证提示的类型
     */
    private ValidationTipType tipType = ValidationTipType.TIP_1;

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
