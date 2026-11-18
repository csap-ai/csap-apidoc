package ai.csap.apidoc.mock;

import ai.csap.apidoc.model.CsapDocParameter;

/**
 * 字段规则里的 {@code mock} 值(规则键 mock,见设计文档 2.6)。
 *
 * @author ycf
 * @since 1.4.0
 */
final class MockValues {

    private MockValues() {
    }

    static Object of(CsapDocParameter p) {
        return p.getMock();
    }
}
