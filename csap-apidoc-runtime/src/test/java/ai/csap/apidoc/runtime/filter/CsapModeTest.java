package ai.csap.apidoc.runtime.filter;

import static ai.csap.apidoc.runtime.filter.Engines.csap;
import static ai.csap.apidoc.runtime.filter.Engines.tree;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ai.csap.apidoc.runtime.filter.Engines.Engine;
import ai.csap.apidoc.runtime.filter.model.ApiResult;
import ai.csap.apidoc.runtime.filter.model.Match;
import ai.csap.apidoc.runtime.filter.model.MatchPage;
import ai.csap.apidoc.runtime.filter.model.Team;
import ai.csap.apidoc.runtime.filter.model.TeamWithExtra;
import ai.csap.validation.factory.FilterClassParam;

/**
 * csap 模式(兼容 csap-framework)。用例改写自 Go 版 mask_test.go。
 */
class CsapModeTest {

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }

    /**
     * 与 Java 文档生成登记 FilterClassParam 的过程一致:
     * 先遍历 home,name 没放行 -> 进 Team 黑名单;之后 away.name 放行,加进白名单,但黑名单优先。
     */
    static TypeRules goSampleRules() {
        FilterClassParam result = new FilterClassParam().type(ApiResult.class);
        result.addIncludes(Arrays.asList("code", "message", "data", "language", "time"));
        result.addExcludes("success");
        FilterClassParam page = new FilterClassParam().type(MatchPage.class);
        page.addIncludes(Arrays.asList("rows", "total"));
        page.addExcludes("size");
        FilterClassParam match = new FilterClassParam().type(Match.class);
        match.addIncludes(Arrays.asList("id", "league_id", "home", "away"));
        match.addExcludes("by_half");
        match.addExcludes("internal");
        FilterClassParam team = new FilterClassParam().type(Team.class);
        team.addIncludes("id");
        team.addExcludes("name");
        team.addExcludes("secret");
        team.addIncludes("name");
        return TypeRules.of(Arrays.asList(result, page, match, team));
    }

    @ParameterizedTest
    @EnumSource(Engine.class)
    void mergedTypeRulesBlacklistFirst(Engine engine) {
        ApiResult<MatchPage> value = new ApiResult<>(new MatchPage());
        String got = engine.write(value, csap(goSampleRules(), value));
        assertEquals(tree("{\"code\":\"0\",\"message\":\"成功\",\"data\":{\"rows\":[{\"id\":7,\"league_id\":3,"
                + "\"home\":{\"id\":1},\"away\":{\"id\":2}}],\"total\":1},\"language\":\"ZH\",\"time\":1}"),
                tree(got), engine + " got " + got);
    }

    @ParameterizedTest
    @EnumSource(Engine.class)
    void emptyIncludesMeansAllButExcludes(Engine engine) {
        // 白名单为空:黑名单之外全部输出;规则对子类型同样生效
        TypeRules rules = TypeRules.EMPTY.and(Team.class, Collections.emptySet(), set("id", "name", "secret"));
        TeamWithExtra value = new TeamWithExtra();
        String got = engine.write(value, csap(rules, value));
        assertEquals(tree("{\"extra\":1}"), tree(got), engine + " got " + got);
    }

    @Test
    void ruleSemantics() {
        TypeRules rules = TypeRules.EMPTY
                .and(Team.class, set("id", "name"), set("name"))
                .and(Object.class, Collections.emptySet(), set("secret"));
        assertTrue(rules.allows(Team.class, "id"));
        assertFalse(rules.allows(Team.class, "name"));
        assertFalse(rules.allows(Team.class, "secret"));
        assertFalse(rules.allows(Team.class, "other"));
        assertTrue(rules.allows(String.class, "other"));
        assertFalse(rules.allows(String.class, "secret"));
        assertTrue(TypeRules.EMPTY.allows(Team.class, "anything"));
    }
}
