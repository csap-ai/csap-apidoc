package ai.csap.validation.boot.autoconfigure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.micrometer.core.instrument.MeterRegistry;

@AutoConfigureMockMvc
abstract class AbstractValidationMvcTest {
    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected DemoApplication.CapturingRecorder recorder;
    @Autowired
    protected MeterRegistry meterRegistry;

    @BeforeEach
    void reset() {
        recorder.violations.clear();
        meterRegistry.clear();
    }

    protected ResultActions listWithoutId() throws Exception {
        return mvc.perform(get("/users"));
    }

    protected ResultActions create(String json) throws Exception {
        return mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected double counter(String endpoint, String rule, String mode) {
        io.micrometer.core.instrument.Counter c = meterRegistry.find("csap.validation.violations")
                .tag("endpoint", endpoint).tag("rule", rule).tag("mode", mode).counter();
        return c == null ? 0 : c.count();
    }
}
