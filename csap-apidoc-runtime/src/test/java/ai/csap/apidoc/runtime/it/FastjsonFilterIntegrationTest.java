package ai.csap.apidoc.runtime.it;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {DemoApplication.class, FastjsonConverterConfig.class}, properties = {
        AbstractFilterIntegrationTest.PROPS_PREFIX + "enabled=true",
        AbstractFilterIntegrationTest.CSAP_OVERRIDE,
        AbstractFilterIntegrationTest.OFF_OVERRIDE})
@AutoConfigureMockMvc
class FastjsonFilterIntegrationTest extends AbstractFilterIntegrationTest {
}
