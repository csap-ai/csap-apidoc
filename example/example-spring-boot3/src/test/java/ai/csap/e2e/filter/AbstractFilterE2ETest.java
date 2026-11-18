package ai.csap.e2e.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.runtime.filter.ResponseRuleRegistry;
import ai.csap.apidoc.service.ApidocContext;

/**
 * 端到端:文档扫描 → 字段规则(YAML 策略)→ DevTools 修改规则 → 下一个请求立即按新规则过滤。
 * 子类决定使用哪种 JSON 库输出。
 */
abstract class AbstractFilterE2ETest {

    static final String PREFIX = "csap.apidoc.runtime.filter.";

    static final String LIST_CSAP = PREFIX + "endpoints[ai.csap.e2e.filter.OrderController.list]=csap";

    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    MockMvc mvc;

    @Autowired
    ResponseRuleRegistry registry;

    JsonNode call(String path) throws Exception {
        return JSON.readTree(mvc.perform(get(path)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * 通过 DevTools 接口设置某个接口的返回字段规则(整体替换)
     */
    void setResponseRules(String method, List<Map<String, Object>> fields) throws Exception {
        Map<String, Object> body = Map.of("className", OrderController.class.getName(), "methodName", method,
                "returnType", Map.of("fields", fields));
        String result = mvc.perform(post("/csap/yaml/addResponseParam").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body))).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(result.contains("true"), result);
    }

    static Map<String, Object> f(String name, Map<?, ?>... children) {
        return children.length == 0 ? Map.of("name", name) : Map.of("name", name, "parameters", List.of(children));
    }

    static TreeSet<String> keys(JsonNode node) {
        TreeSet<String> out = new TreeSet<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(out::add);
        return out;
    }

    static TreeSet<String> set(String... s) {
        return new TreeSet<>(List.of(s));
    }

    void waitForScan() throws InterruptedException {
        // 启动时异步扫描,扫描完成后规则才就绪
        for (int i = 0; i < 300 && !registry.isReady(); i++) {
            Thread.sleep(50);
        }
        assertTrue(registry.isReady(), "文档扫描未在 15 秒内完成");
    }

    @Test
    void filtersByRulesAndHotUpdates() throws Exception {
        waitForScan();
        setResponseRules("detail", List.of());
        setResponseRules("list", List.of());
        // 没有返回规则:原样输出
        assertEquals(set("amount", "customer", "id", "items", "no", "secret", "stock"), keys(call("/e2e/orders/detail")));

        setResponseRules("detail", List.of(f("no"), f("customer", f("name")), f("items", f("sku")),
                f("stock", f("qty"))));
        JsonNode detail = call("/e2e/orders/detail");
        // id 是 forceRep
        assertEquals(set("customer", "id", "items", "no", "stock"), keys(detail), detail.toString());
        assertEquals(set("name"), keys(detail.get("customer")));
        assertEquals(set("sku"), keys(detail.get("items").get(1)));
        // 文档对 Map 字段不展开值的字段(stock 在文档里没有下级),按"没有下级规则则整段输出"处理,与文档一致
        assertEquals(set("cost", "qty", "sku"), keys(detail.get("stock").get("w1")), detail.toString());

        // 输出的字段与文档显示的返回字段一致
        assertEquals(docFields("detail"), keys(detail));

        // DevTools 修改规则后,下一个请求立即生效
        setResponseRules("detail", List.of(f("amount")));
        assertEquals(set("amount", "id"), keys(call("/e2e/orders/detail")));

        // 没配置规则的接口不受影响
        assertEquals(set("amount", "customer", "id", "items", "no", "secret", "stock"), keys(call("/e2e/orders/plain")));
    }

    @Test
    void csapModeOverrideUsesTypeRules() throws Exception {
        waitForScan();
        setResponseRules("list", List.of(f("no"), f("customer", f("id"))));
        JsonNode list = call("/e2e/orders/list");
        assertEquals(2, list.size(), list.toString());
        for (JsonNode order : list) {
            assertEquals(set("customer", "id", "no"), keys(order), list.toString());
            assertEquals(set("id"), keys(order.get("customer")));
        }
        setResponseRules("list", List.of());
    }

    private TreeSet<String> docFields(String method) {
        List<CsapDocMethod> methods = new ArrayList<>();
        ApidocContext.cmd(false, false).getApiList().stream()
                .filter(c -> OrderController.class.getName().equals(c.getName()))
                .forEach(c -> methods.addAll(c.getMethodList()));
        CsapDocMethod m = methods.stream().filter(x -> method.equals(x.getName())).findFirst().orElseThrow();
        return m.getResponse().get(0).getParameters().stream().map(CsapDocParameter::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
