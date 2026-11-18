package ai.csap.apidoc.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.boot.autoconfigure.dbe2e.DbE2eController;
import ai.csap.apidoc.database.DatabaseParamGroupStrategy;
import ai.csap.apidoc.database.autoconfigure.DatabaseAutoConfiguration;
import ai.csap.apidoc.mock.autoconfigure.MockAutoConfiguration;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.strategy.store.RuleJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mock 与 JSON Schema 导入导出的端到端:真实的 Spring MVC 映射 + 数据库模式(H2)规则存储。
 */
class MockSchemaE2eTest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final String CLASS = DbE2eController.class.getName();

    @Configuration
    @EnableWebMvc
    @Import(DbE2eController.class)
    @EnableApidoc(value = "ai.csap.apidoc.boot.autoconfigure.dbe2e", paramType = ApiStrategyType.DATABASE)
    static class App {
        @Bean
        DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:mock_schema_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            return ds;
        }
    }

    private WebApplicationContextRunner runner(String... props) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ApidocAutoConfiguration.class, DatabaseAutoConfiguration.class,
                        MockAutoConfiguration.class))
                .withUserConfiguration(App.class)
                .withPropertyValues("csap.apidoc.database.poll-interval=0", "csap.apidoc.devtool.enabled=true")
                .withPropertyValues(props);
    }

    private static CsapDocMethod method(String name) {
        return ApidocContext.cmd(true, false).getApiList().stream().flatMap(c -> c.getMethodList().stream())
                            .filter(m -> name.equals(m.getName())).findFirst().orElseThrow();
    }

    private static String body(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void mockIsOffByDefault() {
        runner().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) ctx).build();
            assertThat(mvc.perform(get("/csap/mock/dbe2e/query")).andReturn().getResponse().getStatus()).isEqualTo(404);
            assertThat(mvc.perform(get("/csap/mock-status")).andReturn().getResponse().getStatus()).isEqualTo(404);
            // 导入端点默认也不注册
            assertThat(mvc.perform(post("/csap/schema/import").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus()).isIn(404, 405);
        });
    }

    @Test
    void mockFollowsDocumentedStructureAndRules() {
        runner("csap.apidoc.mock.enabled=true", "csap.apidoc.mock.seed=csap").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            DatabaseParamGroupStrategy s = ctx.getBean(DatabaseParamGroupStrategy.class);
            // 返回字段:id(编号)带规则 mock,name 带 example
            s.store().save(CLASS + ".save", RuleJson.write(RuleJson.read("{\"response\":{"
                    + "\"id\":{\"include\":true,\"mock\":\"@int(100,100)\"},"
                    + "\"name\":{\"include\":true,\"example\":\"Tom\"}}}")), "test");
            s.reload();
            ApidocContext.cmd(true, false);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) ctx).build();

            MvcResult ok = mvc.perform(post("/csap/mock/dbe2e/save").contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andReturn();
            assertThat(ok.getResponse().getStatus()).isEqualTo(200);
            assertThat(ok.getResponse().getHeader("X-CSAP-Mock")).isEqualTo("1");
            assertThat(ok.getResponse().getHeader("X-CSAP-Mock-Operation")).isEqualTo(CLASS + ".save");
            assertThat(ok.getResponse().getContentType()).startsWith("application/json");
            JsonNode j = JSON.readTree(body(ok));
            assertThat(j.get("id").asInt()).isEqualTo(100);
            assertThat(j.get("name").asText()).isEqualTo("Tom");

            // 同一种子可复现;请求头换种子不影响固定值,但 X-CSAP-Mock-Seed 回显
            MvcResult seeded = mvc.perform(post("/csap/mock/dbe2e/save").header("X-CSAP-Mock-Seed", "other")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
            assertThat(seeded.getResponse().getHeader("X-CSAP-Mock-Seed")).isEqualTo("other");

            // 路径对、方法不对:405 + Allow;路径不存在:404
            MvcResult wrongMethod = mvc.perform(get("/csap/mock/dbe2e/save")).andReturn();
            assertThat(wrongMethod.getResponse().getStatus()).isEqualTo(405);
            assertThat(wrongMethod.getResponse().getHeader("Allow")).isEqualTo("POST");
            MvcResult none = mvc.perform(get("/csap/mock/not/documented")).andReturn();
            assertThat(none.getResponse().getStatus()).isEqualTo(404);
            assertThat(JSON.readTree(body(none)).get("code").asText()).isEqualTo("MOCK_NOT_FOUND");

            // 文档页探测
            JsonNode status = JSON.readTree(body(mvc.perform(get("/csap/mock-status")).andReturn()));
            assertThat(status.get("enabled").asBoolean()).isTrue();
            assertThat(status.get("prefix").asText()).isEqualTo("/csap/mock");
        });
    }

    @Test
    void schemaExportAndImport() {
        runner("csap.apidoc.schema.import-enabled=true").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            DatabaseParamGroupStrategy s = ctx.getBean(DatabaseParamGroupStrategy.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) ctx).build();
            String key = CLASS + ".save";
            // 请求字段:数据库模式下字段来自规则
            s.store().save(key, RuleJson.write(RuleJson.read("{\"request\":{"
                    + "\"req.name\":{\"include\":true,\"description\":\"名称\"},"
                    + "\"req.age\":{\"include\":true,\"description\":\"年龄\"}}}")), "test");
            s.reload();
            ApidocContext.cmd(true, false);

            // 导出:合集 + 单接口
            JsonNode bundle = JSON.readTree(body(mvc.perform(get("/csap/schema.json")).andReturn()));
            assertThat(bundle.get("$schema").asText()).contains("2020-12");
            assertThat(bundle.get("x-csap-operations").findValuesAsText("operationId")).contains(key);
            JsonNode one = JSON.readTree(body(mvc.perform(get("/csap/schema/method").param("key", key)).andReturn()));
            assertThat(one.get("method").asText()).isEqualTo("POST");
            assertThat(one.get("path").asText()).isEqualTo("/dbe2e/save");
            JsonNode requestBody = one.get("request").get("properties").get("body");
            assertThat(requestBody.get("properties").has("name")).isTrue();
            assertThat(mvc.perform(get("/csap/schema/method").param("key", "no.such")).andReturn().getResponse().getStatus())
                    .isEqualTo(404);

            // 导入:把导出的 Schema 改一改再导回。先预览,不写
            ((com.fasterxml.jackson.databind.node.ObjectNode) requestBody.get("properties").get("name"))
                    .put("description", "新说明").put("minLength", 2).put("maxLength", 8).put("pattern", "^[A-Z]+$");
            ((com.fasterxml.jackson.databind.node.ObjectNode) requestBody).putArray("required").add("name");
            ((com.fasterxml.jackson.databind.node.ObjectNode) requestBody.get("properties")).putObject("ghost")
                    .put("type", "string");
            String req = JSON.writeValueAsString(JSON.createObjectNode().put("key", key).put("kind", "request")
                    .set("schema", one.get("request")));
            JsonNode preview = JSON.readTree(body(mvc.perform(post("/csap/schema/import")
                    .contentType(MediaType.APPLICATION_JSON).content(req)).andReturn())).get("data");
            assertThat(preview.get("applied").asBoolean()).isFalse();
            assertThat(preview.get("matched").asInt()).isGreaterThanOrEqualTo(1);
            assertThat(preview.get("unmatched").findValuesAsText("path")).contains("body.ghost");
            // Length 在 Java 规则里没有对应类型 → 跳过并报告
            assertThat(preview.get("skipped").findValuesAsText("keyword")).contains("Length");
            assertThat(preview.get("yaml").asText()).contains("新说明").contains(CLASS);
            assertThat(s.store().load().rules().get(key).toString()).doesNotContain("新说明");

            // 应用:走规则保存路径,修改人 schema-import,文档立即更新
            JsonNode applied = JSON.readTree(body(mvc.perform(post("/csap/schema/import")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(req.replace("\"kind\":\"request\"", "\"kind\":\"request\",\"apply\":true"))).andReturn()))
                    .get("data");
            assertThat(applied.get("applied").asBoolean()).isTrue();
            assertThat(s.store().load().rules().get(key).toString()).contains("新说明");
            CsapDocParameter name = method("save").getRequest().get(0).getParameters().stream()
                    .filter(p -> "name".equals(p.getName())).findFirst().orElseThrow();
            assertThat(name.getRequired()).isTrue();
            assertThat(name.getValidate()).anyMatch(v -> "^[A-Z]+$".equals(v.getPattern()));
            assertThat(s.history(key, 10).get(0).getUpdatedBy()).isEqualTo("schema-import");
        });
    }
}
