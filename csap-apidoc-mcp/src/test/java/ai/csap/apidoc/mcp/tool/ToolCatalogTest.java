package ai.csap.apidoc.mcp.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import ai.csap.apidoc.mcp.config.McpSettings;
import ai.csap.apidoc.mcp.config.ToolSetting;
import ai.csap.apidoc.mcp.invoke.LoopbackInvoker;
import ai.csap.apidoc.mcp.protocol.JsonRpc;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.type.ModelType;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;

class ToolCatalogTest {

    static HandlerIndex index;

    @BeforeAll
    static void mappings() throws Exception {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean(ItemController.class);
        ctx.refresh();
        RequestMappingHandlerMapping m = new RequestMappingHandlerMapping();
        m.setApplicationContext(ctx);
        m.afterPropertiesSet();
        index = new HandlerIndex(Collections.singletonList(m));
    }

    static CsapDocParameter p(String name, String type, boolean required, String desc) {
        CsapDocParameter p = new CsapDocParameter();
        p.setName(name);
        p.setDataType(type);
        p.setRequired(required);
        p.setValue(desc);
        return p;
    }

    static CsapDocModel base(String paramName, CsapDocParameter p) {
        CsapDocModel m = new CsapDocModel();
        m.setMethodParamName(paramName);
        m.setModelType(ModelType.BASE_DATA);
        m.setParameters(new ArrayList<>(Collections.singletonList(p)));
        return m;
    }

    static CsapDocMethod method(String name, RequestMethod http, String title, CsapDocModel... request) {
        CsapDocMethod m = new CsapDocMethod();
        m.setName(name);
        m.setKey(ItemController.class.getName() + "." + name);
        m.setClassName(ItemController.class.getName());
        m.setSimpleName("ItemController");
        m.setValue(title);
        m.setMethods(new ArrayList<>(Collections.singletonList(http)));
        m.setRequest(new ArrayList<>(Arrays.asList(request)));
        return m;
    }

    static CsapDocResponse doc() {
        CsapDocModel body = new CsapDocModel();
        body.setMethodParamName("item");
        body.setModelType(ModelType.OBJECT);
        body.setValue("商品");
        body.setParameters(new ArrayList<>(Arrays.asList(p("name", "String", true, "名称"), p("qty", "Integer", false, "数量"))));
        CsapDocModel query = new CsapDocModel();
        query.setMethodParamName("query");
        query.setModelType(ModelType.OBJECT);
        query.setParameters(new ArrayList<>(Arrays.asList(p("keyword", "String", false, "关键字"), p("page", "Integer", false, "页码"))));
        CsapDocModelController c = new CsapDocModelController();
        c.setName(ItemController.class.getName());
        c.setSimpleName("ItemController");
        CsapDocMethod hidden = method("upload", RequestMethod.POST, "上传");
        // 文档里路径变量常被记成 BODY / DEFAULT,位置以 Spring 注解为准
        c.setMethodList(new ArrayList<>(Arrays.asList(
                method("get", RequestMethod.GET, "查询商品", base("id", p("id", "Long", true, "商品 ID")),
                        base("tenant", p("X-Tenant", "String", true, "租户"))),
                method("search", RequestMethod.GET, "搜索商品", query),
                method("create", RequestMethod.POST, "新建商品", body),
                method("delete", RequestMethod.DELETE, "删除商品", base("id", p("id", "Long", true, "商品 ID"))),
                hidden)));
        CsapDocResponse r = new CsapDocResponse();
        r.setApiList(new ArrayList<>(Collections.singletonList(c)));
        return r;
    }

    @Test
    void buildsToolsWithDefaultExposure() {
        ToolCatalog cat = ToolCatalog.build(doc(), index, McpSettings.empty(), true);
        assertEquals(5, cat.getAll().size());
        assertEquals(Arrays.asList("Item_get", "Item_search"), new ArrayList<>(cat.getExposed().keySet()));
        McpTool get = cat.getExposed().get("Item_get");
        assertEquals("GET", get.getHttpMethod());
        assertEquals("/items/{id}", get.getPathPattern());
        JsonNode s = get.getInputSchema();
        assertEquals("integer", s.at("/properties/id/type").asText());
        assertEquals("商品 ID", s.at("/properties/id/description").asText());
        assertTrue(s.at("/properties/X-Tenant/description").asText().contains("请求头 X-Tenant"));
        assertEquals("string", s.at("/properties/fields/type").asText());
        assertEquals("[\"id\",\"X-Tenant\"]", JsonRpc.write(s.get("required")));
        assertTrue(get.getDescription().endsWith("HTTP GET /items/{id}"), get.getDescription());

        McpTool search = cat.getExposed().get("Item_search");
        assertEquals("关键字", search.getInputSchema().at("/properties/keyword/description").asText());

        McpTool create = cat.byMethodKey(ItemController.class.getName() + ".create");
        assertFalse(create.isExposed());
        assertEquals("object", create.getInputSchema().at("/properties/body/type").asText());
        assertEquals("[\"name\"]", JsonRpc.write(create.getInputSchema().at("/properties/body/required")));

        McpTool upload = cat.byMethodKey(ItemController.class.getName() + ".upload");
        assertNotNull(upload.getUnsupportedReason());
    }

    @Test
    void settingsOverrideExposureAndNames() {
        McpSettings s = McpSettings.empty();
        String k = ItemController.class.getName() + ".";
        s.getTools().put(k + "create", new ToolSetting(true, "create_item", "新建一个商品"));
        s.getTools().put(k + "get", new ToolSetting(false, null, null));
        // 非法名回退默认名
        s.getTools().put(k + "search", new ToolSetting(null, "bad name!", null));
        s.getTools().put(k + "upload", new ToolSetting(true, null, null));
        ToolCatalog cat = ToolCatalog.build(doc(), index, s, true);
        assertEquals(Arrays.asList("Item_search", "create_item"), new ArrayList<>(cat.getExposed().keySet()));
        assertEquals("新建一个商品", cat.getExposed().get("create_item").getDescription());
        assertNull(cat.getExposed().get("Item_upload"));

        // 关闭 GET 默认暴露
        assertTrue(ToolCatalog.build(doc(), index, McpSettings.empty(), false).getExposed().isEmpty());
    }

    @Test
    void namesAreSanitizedAndUnique() {
        assertEquals("Order_list", ToolCatalog.defaultName("OrderController", "list"));
        assertEquals("Controller_x", ToolCatalog.defaultName("Controller", "x"));
        assertEquals(64, ToolCatalog.defaultName("A", "very_long_method_name_that_goes_on_and_on_and_on_and_on_and_on_and_on").length());
        assertTrue(ToolCatalog.NAME_PATTERN.matcher(ToolCatalog.defaultName("Ä$", "m")).matches());
    }

    @Test
    void invokerBuildsRealRequests() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> seen = new AtomicReference<>();
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            seen.set(ex.getRequestMethod() + " " + ex.getRequestURI().getRawPath()
                    + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery())
                    + " tenant=" + ex.getRequestHeaders().getFirst("X-Tenant")
                    + " auth=" + ex.getRequestHeaders().getFirst("Authorization")
                    + " tool=" + ex.getRequestHeaders().getFirst(LoopbackInvoker.TOOL_HEADER)
                    + " body=" + body);
            byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            ex.sendResponseHeaders(201, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/ctx";
            LoopbackInvoker inv = new LoopbackInvoker(() -> base, null);
            McpSettings s = McpSettings.empty();
            s.getTools().put(ItemController.class.getName() + ".create", new ToolSetting(true, null, null));
            ToolCatalog cat = ToolCatalog.build(doc(), index, s, true);

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer upstream");
            headers.put("Host", "evil");
            LoopbackInvoker.Result r = inv.invoke(cat.getExposed().get("Item_get"),
                    JsonRpc.MAPPER.readTree("{\"id\":\"a/../b\",\"X-Tenant\":\"t1\",\"fields\":\"a b\"}"), headers);
            assertEquals(201, r.getStatus());
            assertEquals("{\"ok\":true}", r.getBody());
            assertEquals("GET /ctx/items/a%2F..%2Fb?fields=a%20b tenant=t1 auth=Bearer upstream tool=Item_get body=", seen.get());

            inv.invoke(cat.getExposed().get("Item_create"), JsonRpc.MAPPER.readTree("{\"body\":{\"name\":\"n\",\"qty\":2}}"),
                    new HashMap<>());
            assertEquals("POST /ctx/items tenant=null auth=null tool=Item_create body={\"name\":\"n\",\"qty\":2}", seen.get());

            inv.invoke(cat.getExposed().get("Item_search"), JsonRpc.MAPPER.readTree("{\"keyword\":\"中文\",\"page\":2}"), null);
            assertTrue(seen.get().startsWith("GET /ctx/items?keyword=%E4%B8%AD%E6%96%87&page=2 "), seen.get());

            assertThrows(LoopbackInvoker.InvalidArgumentsException.class,
                    () -> inv.invoke(cat.getExposed().get("Item_get"), JsonRpc.MAPPER.readTree("{\"X-Tenant\":\"t\"}"), null));
            assertEquals("/items/%2E%2E", inv.buildPathAndQuery(cat.getExposed().get("Item_get"),
                    JsonRpc.MAPPER.readTree("{\"id\":\"..\",\"X-Tenant\":\"t\"}")));
        } finally {
            server.stop(0);
        }
    }
}
