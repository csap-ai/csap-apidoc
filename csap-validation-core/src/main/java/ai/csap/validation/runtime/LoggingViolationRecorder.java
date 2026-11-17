package ai.csap.validation.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.csap.validation.properties.ValidationMode;

/**
 * 用 SLF4J 记录校验失败:observe 模式记 warn,enforce 模式记 debug(请求已被拦截并返回错误)。
 * 只记录接口、字段、规则、code、message,不记录参数值。
 *
 * @author ycf
 * @since 1.2.0
 */
public class LoggingViolationRecorder implements ViolationRecorder {
    private static final Logger LOG = LoggerFactory.getLogger("ai.csap.validation.violations");

    @Override
    public void record(ValidationViolation v) {
        if (v.getMode() == ValidationMode.OBSERVE) {
            LOG.warn("csap validation violation (observe, request passed): endpoint={} field={} rule={} code={} message={}",
                    v.getEndpoint(), v.getField(), v.getRule(), v.getCode(), v.getMessage());
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("csap validation violation (enforce, request rejected): endpoint={} field={} rule={} code={} message={}",
                    v.getEndpoint(), v.getField(), v.getRule(), v.getCode(), v.getMessage());
        }
    }
}
