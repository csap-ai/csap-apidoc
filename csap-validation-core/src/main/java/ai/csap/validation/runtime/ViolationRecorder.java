package ai.csap.validation.runtime;

/**
 * 记录校验失败(observe 与 enforce 两种模式都会调用).
 *
 * <p>可以声明自己的 Bean 替换默认实现,例如上报到告警系统。实现不应抛出异常。
 *
 * @author ycf
 * @since 1.2.0
 */
@FunctionalInterface
public interface ViolationRecorder {
    /**
     * 记录
     *
     * @param violation 校验失败
     */
    void record(ValidationViolation violation);
}
