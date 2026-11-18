package ai.csap.validation.boot.autoconfigure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import ai.csap.validation.properties.ValidationMode;
import ai.csap.validation.runtime.ValidationViolation;

/**
 * observe:记录(自定义记录器 + Micrometer 计数),请求放行.
 */
@SpringBootTest(classes = DemoApplication.class, properties = "csap.validation.mode=observe")
class ObserveModeTest extends AbstractValidationMvcTest {

    @Test
    void recordsAndPasses() throws Exception {
        listWithoutId().andExpect(status().isOk()).andExpect(content().string("ok"));
        create("{\"username\":\"ab\"}").andExpect(status().isOk()).andExpect(content().string("ok"));

        assertThat(recorder.violations).hasSize(2);
        ValidationViolation v = recorder.violations.get(1);
        assertThat(v.getEndpoint()).isEqualTo(DemoApplication.CREATE);
        assertThat(v.getField()).isEqualTo("username");
        assertThat(v.getRule()).isEqualTo("Size");
        assertThat(v.getCode()).isEqualTo("400");
        assertThat(v.getMessage()).isEqualTo("字段username,长度3-20");
        assertThat(v.getMode()).isEqualTo(ValidationMode.OBSERVE);
        assertThat(counter(DemoApplication.LIST, "NotEmpty", "observe")).isEqualTo(1.0);
        assertThat(counter(DemoApplication.CREATE, "Size", "observe")).isEqualTo(1.0);
    }
}
