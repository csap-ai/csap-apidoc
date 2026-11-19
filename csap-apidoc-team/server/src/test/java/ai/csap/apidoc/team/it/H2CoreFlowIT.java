package ai.csap.apidoc.team.it;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** H2 上的核心流程;同一个 H2 内存库也供其他 H2 集成测试共用。 */
class H2CoreFlowIT extends CoreFlowTests {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        H2Db.register(r);
    }
}
