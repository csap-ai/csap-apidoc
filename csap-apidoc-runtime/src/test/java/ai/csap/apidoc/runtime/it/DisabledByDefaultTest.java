package ai.csap.apidoc.runtime.it;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

import ai.csap.apidoc.event.ApidocRulesRefreshedEvent;
import ai.csap.apidoc.runtime.filter.ResponseFilterAdvice;

/**
 * 默认关闭:不注册任何过滤 Bean,响应不变
 */
@SpringBootTest(classes = DemoApplication.class)
@AutoConfigureMockMvc
class DisabledByDefaultTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationContext context;

    @Autowired
    ApplicationEventPublisher publisher;

    @Test
    void noFilteringWhenDisabled() throws Exception {
        assertTrue(context.getBeansOfType(ResponseFilterAdvice.class).isEmpty());
        publisher.publishEvent(new ApidocRulesRefreshedEvent(DemoApplication.documentV1(), Collections.emptySet()));
        String body = mvc.perform(get("/page")).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"size\"") && body.contains("\"secret\""), body);
    }
}
