package ai.csap.apidoc.runtime.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.apidoc.event.ApidocRulesRefreshedEvent;
import ai.csap.apidoc.runtime.filter.ResponseRuleRegistry;

/**
 * 三种引擎共用的集成测试:MockMvc 走完整的 Spring MVC 流程。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class AbstractFilterIntegrationTest {

    static final String PROPS_PREFIX = "csap.apidoc.runtime.filter.";

    static final String CSAP_OVERRIDE = PROPS_PREFIX + "endpoints[ai.csap.apidoc.runtime.it.DemoController.csapMode]=csap";

    static final String OFF_OVERRIDE = PROPS_PREFIX + "endpoints[ai.csap.apidoc.runtime.it.DemoController.offMode]=off";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    ResponseRuleRegistry registry;

    JsonNode call(String path) throws Exception {
        String body = mvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JSON.readTree(body);
    }

    static JsonNode tree(String json) throws Exception {
        return JSON.readTree(json);
    }

    void publish(ai.csap.apidoc.model.CsapDocResponse doc) {
        publisher.publishEvent(new ApidocRulesRefreshedEvent(doc, Collections.emptySet()));
    }

    void publishV1() {
        publish(DemoApplication.documentV1());
    }

    @Test
    @Order(1)
    void notFilteredBeforeScanThenHotUpdate() throws Exception {
        assertFalse(registry.isReady());
        JsonNode before = call("/page");
        assertTrue(before.has("size"), "扫描完成前不过滤: " + before);

        publishV1();
        JsonNode v1 = call("/page");
        assertEquals(tree("{\"rows\":[{\"id\":7,\"league_id\":3,\"home\":{\"id\":1},\"away\":{\"name\":\"B\"},"
                + "\"by_half\":{\"h1\":{\"name\":\"A\"}}}],\"total\":1}"), v1);

        // 规则变化后,下一个请求立即按新规则输出
        publish(DemoApplication.documentV2());
        assertEquals(tree("{\"total\":1,\"size\":20}"), call("/page"));
        publishV1();
    }

    @Test
    @Order(2)
    void wrapperInReturnType() throws Exception {
        publishV1();
        assertEquals(tree("{\"code\":\"0\",\"message\":\"成功\",\"data\":{\"rows\":[{\"id\":7}],\"total\":1}}"),
                call("/result"));
    }

    @Test
    @Order(3)
    void wrappedByAnotherAdviceOnlyFiltersReturnValue() throws Exception {
        publishV1();
        assertEquals(tree("{\"code\":\"0\",\"message\":\"成功\",\"data\":{\"total\":1},\"language\":\"ZH\","
                + "\"time\":1,\"success\":true}"), call("/wrapped"));
    }

    @Test
    @Order(4)
    void endpointOverrides() throws Exception {
        publishV1();
        assertEquals(tree("{\"rows\":[{\"id\":7,\"home\":{\"id\":1,\"name\":\"A\"}}],\"total\":1}"),
                call("/csapMode"));
        JsonNode off = call("/offMode");
        assertTrue(off.has("size") && off.get("rows").get(0).has("internal"), off.toString());
    }

    @Test
    @Order(5)
    void listAndUntouchedEndpoints() throws Exception {
        publishV1();
        assertEquals(tree("[{\"id\":1},{\"id\":2}]"), call("/list"));
        JsonNode noRules = call("/noRules");
        assertTrue(noRules.has("size") && noRules.get("rows").get(0).get("home").has("secret"), noRules.toString());
        // fastjson 转换器排在第一个时字符串会带引号,与过滤无关
        String text = mvc.perform(get("/text")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals("plain", text.replace("\"", ""));
    }
}
