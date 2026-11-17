package ai.csap.apidoc.runtime.it;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {DemoApplication.class, Fastjson2ConverterConfig.class}, properties = {
        AbstractFilterIntegrationTest.PROPS_PREFIX + "enabled=true",
        AbstractFilterIntegrationTest.PROPS_PREFIX + "engine=fastjson2",
        AbstractFilterIntegrationTest.CSAP_OVERRIDE,
        AbstractFilterIntegrationTest.OFF_OVERRIDE})
@AutoConfigureMockMvc
class Fastjson2FilterIntegrationTest extends AbstractFilterIntegrationTest {
}
