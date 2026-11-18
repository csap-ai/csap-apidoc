package ai.csap.validation.boot.autoconfigure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 全局 off,只对 create 开 enforce、对 list 开 observe.
 */
@SpringBootTest(classes = DemoApplication.class, properties = {
        "csap.validation.endpoints[ai.csap.validation.boot.autoconfigure.web.DemoController.create]=enforce",
        "csap.validation.endpoints[ai.csap.validation.boot.autoconfigure.web.DemoController.list]=observe"})
class EndpointOverrideTest extends AbstractValidationMvcTest {

    @Test
    void perEndpointModes() throws Exception {
        create("{}").andExpect(jsonPath("$.code").value("400"));
        listWithoutId().andExpect(content().string("ok"));
        assertThat(recorder.violations).extracting(v -> v.getMode().name()).containsExactly("ENFORCE", "OBSERVE");
    }
}
