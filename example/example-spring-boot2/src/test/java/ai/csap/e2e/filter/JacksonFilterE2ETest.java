package ai.csap.e2e.filter;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = FilterE2EApplication.class, properties = {
        AbstractFilterE2ETest.PREFIX + "enabled=true", AbstractFilterE2ETest.LIST_CSAP,
        "csap.apidoc.devtool.enabled=true", AbstractFilterE2ETest.NO_VALIDATION})
@AutoConfigureMockMvc(addFilters = false)
class JacksonFilterE2ETest extends AbstractFilterE2ETest {
}
