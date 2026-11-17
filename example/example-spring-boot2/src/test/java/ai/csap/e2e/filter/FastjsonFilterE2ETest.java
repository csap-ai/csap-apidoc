package ai.csap.e2e.filter;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import ai.csap.e2e.config.FastjsonConverter;

@SpringBootTest(classes = {FilterE2EApplication.class, FastjsonConverter.class}, properties = {
        AbstractFilterE2ETest.PREFIX + "enabled=true", AbstractFilterE2ETest.LIST_CSAP,
        "csap.apidoc.devtool.enabled=true", AbstractFilterE2ETest.NO_VALIDATION})
@AutoConfigureMockMvc(addFilters = false)
class FastjsonFilterE2ETest extends AbstractFilterE2ETest {
}
