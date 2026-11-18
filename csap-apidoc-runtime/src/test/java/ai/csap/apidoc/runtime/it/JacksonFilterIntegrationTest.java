package ai.csap.apidoc.runtime.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.apidoc.runtime.filter.JacksonFilterSupport;
import ai.csap.apidoc.runtime.filter.JacksonResponseFilterEngine;

@SpringBootTest(classes = DemoApplication.class, properties = {
        AbstractFilterIntegrationTest.PROPS_PREFIX + "enabled=true",
        AbstractFilterIntegrationTest.CSAP_OVERRIDE,
        AbstractFilterIntegrationTest.OFF_OVERRIDE})
@AutoConfigureMockMvc
class JacksonFilterIntegrationTest extends AbstractFilterIntegrationTest {

    @Autowired
    ObjectMapper applicationObjectMapper;

    @Autowired
    RequestMappingHandlerAdapter adapter;

    @Autowired
    JacksonResponseFilterEngine engine;

    @Test
    void jsonViewStillApplies() throws Exception {
        publishV1();
        // 规则放行 a、c;@JsonView 再去掉没有标 view 的 c
        assertEquals(tree("{\"a\":\"a\"}"), call("/viewed"));
    }

    @Test
    void objectMappersAreNotModified() {
        assertFalse(JacksonFilterSupport.isInstalled(applicationObjectMapper), "应用的 ObjectMapper 不应被修改");
        MappingJackson2HttpMessageConverter converter = adapter.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast).findFirst().orElseThrow();
        assertSame(applicationObjectMapper, converter.getObjectMapper());
        assertFalse(JacksonFilterSupport.isInstalled(converter.getObjectMapper()));
        assertTrue(engine.isRegistered(MappingJackson2HttpMessageConverter.class));
    }
}
