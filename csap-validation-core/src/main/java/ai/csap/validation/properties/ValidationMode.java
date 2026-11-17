package ai.csap.validation.properties;

/**
 * 运行时请求参数校验模式.
 *
 * @author ycf
 * @since 1.2.0
 */
public enum ValidationMode {
    /**
     * 不校验(默认)
     */
    OFF,
    /**
     * 观察:校验并记录(日志、指标),请求照常放行
     */
    OBSERVE,
    /**
     * 拦截:校验失败直接返回错误,不进入接口方法
     */
    ENFORCE
}
