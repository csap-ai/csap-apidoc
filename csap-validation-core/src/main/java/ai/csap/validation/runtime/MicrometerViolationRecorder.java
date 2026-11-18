package ai.csap.validation.runtime;

import java.util.Locale;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 校验失败计数:{@code csap.validation.violations{endpoint, rule, mode}}.
 *
 * <p>只有 classpath 中有 Micrometer 时才会被加载。
 *
 * @author ycf
 * @since 1.2.0
 */
public class MicrometerViolationRecorder implements ViolationRecorder {
    /**
     * 指标名
     */
    public static final String METER = "csap.validation.violations";

    private final MeterRegistry registry;

    public MicrometerViolationRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void record(ValidationViolation v) {
        Counter.builder(METER)
                .description("CSAP request parameter validation violations")
                .tag("endpoint", String.valueOf(v.getEndpoint()))
                .tag("rule", String.valueOf(v.getRule()))
                .tag("mode", v.getMode().name().toLowerCase(Locale.ROOT))
                .register(registry)
                .increment();
    }
}
