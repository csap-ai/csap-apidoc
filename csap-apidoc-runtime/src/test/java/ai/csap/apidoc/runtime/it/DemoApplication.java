package ai.csap.apidoc.runtime.it;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.runtime.filter.ResponseRuleRegistry;
import ai.csap.apidoc.runtime.filter.model.ApiResult;
import ai.csap.apidoc.runtime.filter.model.Match;
import ai.csap.apidoc.runtime.filter.model.MatchPage;
import ai.csap.apidoc.runtime.filter.model.Team;
import ai.csap.apidoc.type.ModelType;
import ai.csap.validation.factory.FilterClassParam;

/**
 * 集成测试应用。规则不经过文档扫描,直接构造文档模型并发布刷新事件(扫描到过滤的完整链路在示例项目里测)。
 */
@SpringBootApplication
public class DemoApplication {

    static final String CONTROLLER = DemoController.class.getName();

    /**
     * 配置了返回规则的接口
     */
    static final Set<String> WITH_RULES = ConcurrentHashMap.newKeySet();

    /**
     * csap 模式规则
     */
    static final Map<String, List<FilterClassParam>> TYPE_RULES = new ConcurrentHashMap<>();

    @Bean
    public ResponseRuleRegistry testResponseRuleRegistry() {
        return new ResponseRuleRegistry(WITH_RULES::contains,
                k -> TYPE_RULES.getOrDefault(k, Collections.emptyList()));
    }

    static CsapDocParameter p(String name, CsapDocParameter... children) {
        return p(name, null, children);
    }

    static CsapDocParameter p(String name, String dataType, CsapDocParameter... children) {
        CsapDocModel child = children.length == 0 ? null : CsapDocModel.builder().modelType(ModelType.OBJECT)
                .parameters(new ArrayList<>(Arrays.asList(children))).build();
        return CsapDocParameter.builder().name(name).dataType(dataType).children(child).build();
    }

    static CsapDocMethod m(String name, CsapDocParameter... params) {
        CsapDocModel root = CsapDocModel.builder().modelType(ModelType.OBJECT)
                .parameters(new ArrayList<>(Arrays.asList(params))).build();
        return CsapDocMethod.builder().className(CONTROLLER).name(name).key(CONTROLLER + "." + name)
                .response(new ArrayList<>(Collections.singletonList(root))).build();
    }

    /**
     * 版本 1 的文档
     */
    static CsapDocResponse documentV1() {
        WITH_RULES.clear();
        WITH_RULES.addAll(Arrays.asList(CONTROLLER + ".page", CONTROLLER + ".result", CONTROLLER + ".wrapped",
                CONTROLLER + ".csapMode", CONTROLLER + ".offMode", CONTROLLER + ".list"));
        TYPE_RULES.clear();
        TYPE_RULES.put(CONTROLLER + ".csapMode", csapRules());
        CsapDocParameter rows = p("rows", p("id"), p("leagueId"), p("home", p("id")), p("away", p("name")),
                p("byHalf", "Map<String,Team>", p("name")));
        List<CsapDocMethod> methods = Arrays.asList(
                m("page", rows, p("total")),
                m("result", p("code"), p("message"), p("data", p("rows", p("id")), p("total"))),
                m("wrapped", p("total")),
                m("csapMode", p("total")),
                m("offMode", p("total")),
                m("list", p("id")),
                m("noRules", p("total")));
        CsapDocResponse doc = new CsapDocResponse();
        doc.getApiList().add(CsapDocModelController.builder().name(CONTROLLER)
                .methodList(new ArrayList<>(methods)).build());
        return doc;
    }

    /**
     * 版本 2:page 只放行 total
     */
    static CsapDocResponse documentV2() {
        CsapDocResponse doc = documentV1();
        List<CsapDocMethod> methods = doc.getApiList().get(0).getMethodList();
        methods.set(0, m("page", p("total"), p("size")));
        return doc;
    }

    static List<FilterClassParam> csapRules() {
        FilterClassParam page = new FilterClassParam().type(MatchPage.class);
        page.addIncludes(Arrays.asList("rows", "total"));
        FilterClassParam match = new FilterClassParam().type(Match.class);
        match.addIncludes(Arrays.asList("id", "home"));
        FilterClassParam team = new FilterClassParam().type(Team.class);
        team.addExcludes("secret");
        return Arrays.asList(page, match, team);
    }

    static ApiResult<MatchPage> resultOf(MatchPage page) {
        return new ApiResult<>(page);
    }
}
