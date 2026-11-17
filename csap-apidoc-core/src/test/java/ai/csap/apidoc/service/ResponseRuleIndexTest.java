package ai.csap.apidoc.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ai.csap.validation.factory.IValidateBaseFactory;
import ai.csap.validation.factory.Validate;

/**
 * 字段规则"先构建、再替换":构建期间读到的始终是旧规则,提交后一次性换成新规则。
 */
class ResponseRuleIndexTest {

    private static final String KEY = "com.example.DemoController.get";

    @Test
    void oldRulesStayVisibleUntilCommit() {
        Validate old = new Validate();
        old.filterResponse(String.class).addIncludes("old");
        IValidateBaseFactory.VALIDATE_MAP.put(KEY, old);

        String staging = ResponseRuleIndex.beginBuild(KEY);
        assertTrue(ResponseRuleIndex.isStagingKey(staging));
        IValidateBaseFactory.VALIDATE_MAP.computeIfAbsent(staging, k -> new Validate())
                .filterResponse(String.class).addIncludes("new");
        ResponseRuleIndex.markResponseRule(staging);

        // 构建中:正式键仍是旧规则,且旧规则未被清空
        assertSame(old, IValidateBaseFactory.VALIDATE_MAP.get(KEY));
        assertTrue(old.getFilterClassParams().get(0).containsIncludes("old"));
        assertFalse(ResponseRuleIndex.hasResponseRules(KEY));

        ResponseRuleIndex.commit(KEY, staging);
        Validate now = IValidateBaseFactory.VALIDATE_MAP.get(KEY);
        assertNotSame(old, now);
        assertTrue(now.getFilterClassParams().get(0).containsIncludes("new"));
        assertTrue(ResponseRuleIndex.hasResponseRules(KEY));
        assertFalse(IValidateBaseFactory.VALIDATE_MAP.containsKey(staging));

        // 再次构建且没有命中任何返回规则:标记被清除
        String staging2 = ResponseRuleIndex.beginBuild(KEY);
        ResponseRuleIndex.commit(KEY, staging2);
        assertFalse(ResponseRuleIndex.hasResponseRules(KEY));
    }

    @Test
    void abortKeepsOldRules() {
        Validate old = new Validate();
        IValidateBaseFactory.VALIDATE_MAP.put(KEY + "2", old);
        String staging = ResponseRuleIndex.beginBuild(KEY + "2");
        IValidateBaseFactory.VALIDATE_MAP.put(staging, new Validate());
        ResponseRuleIndex.abort(staging);
        assertSame(old, IValidateBaseFactory.VALIDATE_MAP.get(KEY + "2"));
        assertFalse(IValidateBaseFactory.VALIDATE_MAP.containsKey(staging));
    }
}
