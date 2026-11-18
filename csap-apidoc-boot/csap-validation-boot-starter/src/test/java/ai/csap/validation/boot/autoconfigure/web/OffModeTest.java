package ai.csap.validation.boot.autoconfigure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import ai.csap.validation.ValidateIntercept;
import ai.csap.validation.runtime.ValidatingArgumentResolver;
import ai.csap.validation.runtime.ValidationExecutor;

/**
 * 默认关闭:不注册运行时组件,不包装参数解析器,请求照常.
 */
@SpringBootTest(classes = DemoApplication.class)
class OffModeTest extends AbstractValidationMvcTest {
    @Autowired
    ApplicationContext context;

    @Test
    void offByDefault() throws Exception {
        listWithoutId().andExpect(status().isOk()).andExpect(content().string("ok"));
        create("{\"username\":\"a\"}").andExpect(status().isOk()).andExpect(content().string("ok"));
        assertThat(recorder.violations).isEmpty();
        assertThat(context.getBeanNamesForType(ValidationExecutor.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ValidateIntercept.class)).isEmpty();
        assertThat(context.getBean(RequestMappingHandlerAdapter.class).getArgumentResolvers())
                .noneMatch(r -> r instanceof ValidatingArgumentResolver);
    }
}
