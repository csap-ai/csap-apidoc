package ai.csap.apidoc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.model.ParamGroupMethodProperty;

/**
 * 规则过期检查的判定逻辑(规则来源用 SQLite 策略模型,规则内容直接给出,不读文件)
 */
class RuleConsistencyCheckerTest {

    public static class ItemApi {
        public Object pageItems(String keyword) {
            return null;
        }

        public Object detail(Long id) {
            return null;
        }
    }

    static ParamGroupMethodProperty rule(List<String> request, List<String> response) {
        ParamGroupMethodProperty p = new ParamGroupMethodProperty();
        p.setRequest(new HashMap<>());
        p.setResponse(new HashMap<>());
        request.forEach(k -> p.getRequest().put(k, new ParamGroupMethodProperty.ParamDataValidate()));
        response.forEach(k -> p.getResponse().put(k, new ParamGroupMethodProperty.ParamDataValidate()));
        return p;
    }

    static CsapDocMethod method(String name, String... params) {
        CsapDocMethod m = new CsapDocMethod();
        m.setClassName(ItemApi.class.getName());
        m.setName(name);
        m.setParamNames(Arrays.asList(params));
        return m;
    }

    @Test
    void reportsRenamedMethodsAndMissingPaths() {
        RuleConsistencyChecker.reset();
        CsapDocMethod page = method("pageItems", "keyword");
        CsapDocMethod detail = method("detail", "id");
        // 文档生成时查过的路径
        RuleConsistencyChecker.recordLookup(page, true, "keyword.keyword");
        for (String k : Arrays.asList("code", "data", "data.total", "data.rows", "data.rows.id")) {
            RuleConsistencyChecker.recordLookup(page, false, k);
        }
        RuleConsistencyChecker.recordLookup(detail, false, "data");

        Map<String, ParamGroupMethodProperty> methods = new HashMap<>();
        methods.put("listItems", rule(Collections.emptyList(), Arrays.asList("data", "data.rows", "data.rows.id")));
        methods.put("pageItems", rule(Arrays.asList("keyword.keyword", "kw.kw"),
                Arrays.asList("code", "data", "data.animationUrl", "data.rows.id", "data.rows.gone.deep")));
        // data.x.y:上级 data.x 没被查到(未展开的子树),不误报
        methods.put("detail", rule(Collections.emptyList(), Arrays.asList("data", "data.x.y")));
        Map<String, Map<String, ParamGroupMethodProperty>> rules = new HashMap<>();
        rules.put(ItemApi.class.getName(), methods);
        rules.put("com.example.NotScanned", Collections.singletonMap("x", rule(Collections.emptyList(), Collections.singletonList("a"))));

        StrategyModel model = new StrategyModel();
        model.setParamType(ApiStrategyType.SQL_LITE);
        model.setPath("csap-api-doc");
        model.setClazz(new HashSet<>(Collections.singletonList(ItemApi.class)));
        CsapDocModelController c = new CsapDocModelController();
        c.setName(ItemApi.class.getName());
        c.setMethodList(Arrays.asList(page, detail));
        CsapDocResponse doc = new CsapDocResponse();
        doc.setApiList(Collections.singletonList(c));

        List<String> out = RuleConsistencyChecker.check(Collections.singletonList(model), doc, m -> rules);
        assertEquals(2, out.size(), String.join("\n", out));
        assertTrue(out.get(0).contains(".listItems —— 类中已没有这个方法(规则来源 sql_lite: csap-api-doc);相似的现有方法: pageItems"),
                out.get(0));
        assertTrue(out.get(1).contains(".pageItems —— "), out.get(1));
        assertTrue(out.get(1).contains("返回: data.animationUrl;请求: kw.kw"), out.get(1));
        assertEquals(out, RuleConsistencyChecker.lastResult());

        // YAML 以外、SQLite 以外的来源不检查
        model.setParamType(ApiStrategyType.ANNOTATION);
        assertTrue(RuleConsistencyChecker.check(Collections.singletonList(model), doc, m -> rules).isEmpty());

        // 数据库模式同样检查
        model.setParamType(ApiStrategyType.DATABASE);
        List<String> db = RuleConsistencyChecker.check(Collections.singletonList(model), doc, m -> rules);
        assertEquals(2, db.size(), String.join("\n", db));
        assertTrue(db.get(0).contains("规则来源 database: csap-api-doc"), db.get(0));
    }

    @Test
    void similarNames() {
        assertEquals(Arrays.asList("pageItems"), RuleConsistencyChecker.similar("listItems",
                new java.util.TreeSet<>(Arrays.asList("pageItems", "detail", "hashCode", "toString"))));
        assertEquals(2, RuleConsistencyChecker.distance("kitten", "sitting") - 1);
    }
}
