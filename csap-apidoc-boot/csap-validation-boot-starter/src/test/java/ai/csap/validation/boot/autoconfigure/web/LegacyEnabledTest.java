package ai.csap.validation.boot.autoconfigure.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 1.1.x 的 csap.validation.enabled 仍然有效,以及 http-status.
 */
class LegacyEnabledTest {

    @Nested
    @SpringBootTest(classes = DemoApplication.class, properties = "csap.validation.enabled=true")
    class EnabledMeansEnforce extends AbstractValidationMvcTest {
        @Test
        void enforce() throws Exception {
            listWithoutId().andExpect(status().isOk()).andExpect(jsonPath("$.code").value("1001"));
        }
    }

    @Nested
    @SpringBootTest(classes = DemoApplication.class, properties = {"csap.validation.enabled=true",
            "csap.validation.mode=observe"})
    class ModeWinsOverEnabled extends AbstractValidationMvcTest {
        @Test
        void observe() throws Exception {
            listWithoutId().andExpect(content().string("ok"));
        }
    }

    @Nested
    @SpringBootTest(classes = DemoApplication.class, properties = {"csap.validation.enabled=false",
            "csap.validation.mode=enforce"})
    class DisabledTurnsEverythingOff extends AbstractValidationMvcTest {
        @Test
        void off() throws Exception {
            listWithoutId().andExpect(content().string("ok"));
        }
    }

    @Nested
    @SpringBootTest(classes = DemoApplication.class, properties = {"csap.validation.mode=enforce",
            "csap.validation.http-status=400"})
    class HttpStatus extends AbstractValidationMvcTest {
        @Test
        void configuredStatus() throws Exception {
            listWithoutId().andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("1001"))
                    .andExpect(jsonPath("$.message").value("字段id,ID不能为空"));
        }
    }
}
