package ai.csap.apidoc.service.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;

import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;

/**
 * Postman 导出:URL 不能出现 {{baseUrl}}//path;参数 required 为 null 时不能空指针。
 */
class PostmanConverterServiceUrlTest {

    private final PostmanConverterService service = new PostmanConverterService();

    private static CsapDocParameter queryParam(String name, Boolean required) {
        CsapDocParameter p = new CsapDocParameter();
        p.setName(name);
        p.setParamType(ParamType.QUERY);
        p.setRequired(required);
        return p;
    }

    private static CsapDocResponse doc(String controllerPath, String methodPath, List<CsapDocParameter> params) {
        CsapDocModel request = new CsapDocModel();
        request.setParameters(params);
        CsapDocMethod method = new CsapDocMethod();
        method.setName("list");
        method.setValue("列表");
        method.setPaths(new String[]{methodPath});
        method.setMethods(new ArrayList<>(List.of(RequestMethod.GET)));
        method.setParamType(ParamType.QUERY);
        method.setParamNames(new ArrayList<>(List.of("query")));
        method.setMethodHeaders(new ArrayList<>());
        method.setRequest(new ArrayList<>(List.of(request)));
        CsapDocModelController controller = new CsapDocModelController();
        controller.setName("com.example.UserController");
        controller.setValue("用户");
        controller.setPath(controllerPath == null ? null : new String[]{controllerPath});
        controller.setMethodList(new ArrayList<>(List.of(method)));
        CsapDocResponse response = new CsapDocResponse();
        response.getApiList().add(controller);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> url(CsapDocResponse doc) {
        Map<String, Object> collection = service.convertToPostmanCollectionMap(doc);
        Map<String, Object> folder = ((List<Map<String, Object>>) collection.get("item")).get(0);
        Map<String, Object> item = ((List<Map<String, Object>>) folder.get("item")).get(0);
        return (Map<String, Object>) ((Map<String, Object>) item.get("request")).get("url");
    }

    @Test
    void rawUrlHasSingleSlashAfterBaseUrl() {
        Map<String, Object> url = url(doc("/api", "/users", new ArrayList<>()));
        assertEquals("{{baseUrl}}/api/users", url.get("raw"));
        assertEquals(List.of("api", "users"), url.get("path"));
    }

    @Test
    void trailingAndLeadingSlashesAreCollapsed() {
        assertEquals("{{baseUrl}}/api/users", url(doc("/api/", "/users", new ArrayList<>())).get("raw"));
        assertEquals("{{baseUrl}}/users", url(doc(null, "/users", new ArrayList<>())).get("raw"));
        assertFalse(service.convertToPostmanCollection(doc("/api", "/users", new ArrayList<>())).contains("}}//"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullRequiredDoesNotThrowAndIsTreatedAsOptional() {
        List<CsapDocParameter> params = new ArrayList<>();
        params.add(queryParam("page", null));
        params.add(queryParam("id", Boolean.TRUE));
        List<Map<String, Object>> query = (List<Map<String, Object>>) url(doc("/api", "/users", params)).get("query");
        assertEquals(2, query.size());
        assertEquals(Boolean.TRUE, query.get(0).get("disabled"));
        // 序列化时只输出 disabled=true,启用的参数不带该字段
        assertFalse(query.get(1).containsKey("disabled"));
    }
}
