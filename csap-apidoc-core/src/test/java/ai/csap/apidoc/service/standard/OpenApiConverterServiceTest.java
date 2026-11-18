package ai.csap.apidoc.service.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.response.CsapDocParentResponse;
import ai.csap.apidoc.type.ModelType;

/**
 * OpenAPI 导出:普通扫描出的接口(apiList)要生成路径;required 为 null 不能空指针;输出必须是合法 JSON。
 */
class OpenApiConverterServiceTest {

    private final OpenApiConverterService service = new OpenApiConverterService();
    private final ObjectMapper mapper = new ObjectMapper();

    private static CsapDocParameter param(String name, ParamType type, Boolean required, String dataType) {
        CsapDocParameter p = new CsapDocParameter();
        p.setName(name);
        p.setParamType(type);
        p.setRequired(required);
        p.setDataType(dataType);
        p.setValue(name + " 说明");
        return p;
    }

    private static CsapDocModel model(ParamType type, CsapDocParameter... params) {
        CsapDocModel m = new CsapDocModel();
        m.setParamType(type);
        m.setParameters(new ArrayList<>(List.of(params)));
        return m;
    }

    private static CsapDocMethod method(String key, String path, RequestMethod http, ParamType paramType,
                                        List<CsapDocModel> request, List<CsapDocModel> response) {
        CsapDocMethod m = new CsapDocMethod();
        m.setKey(key);
        m.setName(key);
        m.setValue(key + " 接口");
        m.setPaths(new String[]{path});
        m.setMethods(new ArrayList<>(List.of(http)));
        m.setParamType(paramType);
        m.setRequest(request);
        m.setResponse(response);
        return m;
    }

    private static CsapDocResponse doc() {
        CsapDocParameter child = param("street", ParamType.BODY, null, "String");
        CsapDocModel childModel = model(ParamType.BODY, child);
        CsapDocParameter address = param("address", ParamType.BODY, true, "Address");
        address.setChildren(childModel);
        address.setModelType(ModelType.OBJECT);
        CsapDocParameter tags = param("tags", ParamType.BODY, null, "List<String>");
        tags.setModelType(ModelType.ARRAY);

        CsapDocMethod list = method("com.example.UserController.list", "/list", RequestMethod.GET, ParamType.QUERY,
                List.of(model(ParamType.DEFAULT, param("name", ParamType.QUERY, null, "String"),
                        param("page", ParamType.DEFAULT, true, "Integer"))),
                List.of(model(ParamType.DEFAULT, param("total", null, null, "Long"))));
        CsapDocMethod get = method("com.example.UserController.get", "/{id:\\d+}", RequestMethod.GET, ParamType.DEFAULT,
                List.of(model(ParamType.DEFAULT, param("id", ParamType.PATH, null, "Long"))), null);
        CsapDocMethod save = method("com.example.UserController.save", "save", RequestMethod.POST, ParamType.BODY,
                List.of(model(ParamType.BODY, param("username", ParamType.BODY, true, "String"),
                        param("age", ParamType.BODY, null, "int"), address, tags)), null);
        CsapDocMethod hidden = method("com.example.UserController.hidden", "/hidden", RequestMethod.GET, ParamType.QUERY, null, null);
        hidden.setHidden(true);

        CsapDocModelController controller = new CsapDocModelController();
        controller.setName("com.example.UserController");
        controller.setSimpleName("UserController");
        controller.setValue("用户");
        controller.setDescription("用户管理");
        controller.setPath(new String[]{"/api/user/"});
        controller.setMethodList(new ArrayList<>(List.of(list, get, save, hidden)));

        CsapDocResponse response = new CsapDocResponse();
        response.getApiList().add(controller);
        return response;
    }

    private JsonNode convert(CsapDocResponse doc) throws Exception {
        return mapper.readTree(service.convertToOpenApi(doc));
    }

    @Test
    void scannedApiListGeneratesPaths() throws Exception {
        JsonNode root = convert(doc());
        assertEquals(OpenApiConverterService.OPENAPI_VERSION, root.get("openapi").asText());
        JsonNode paths = root.get("paths");
        assertTrue(paths.has("/api/user/list"));
        assertTrue(paths.has("/api/user/{id}"), "路径变量中的正则应去掉");
        assertTrue(paths.has("/api/user/save"));
        assertFalse(paths.has("/api/user/hidden"));
        assertEquals("用户", root.get("tags").get(0).get("name").asText());
        assertEquals("com.example.UserController.list", paths.get("/api/user/list").get("get").get("operationId").asText());
    }

    @Test
    void statusIsExportedAsDeprecatedAndExtension() throws Exception {
        CsapDocResponse doc = doc();
        List<CsapDocMethod> methods = doc.getApiList().get(0).getMethodList();
        methods.get(0).setStatus(ai.csap.apidoc.annotation.ApiStatus.DEPRECATED);
        methods.get(0).setStatusNote("请改用 /v2/list");
        methods.get(0).setDescription("原说明");
        methods.get(2).setStatus(ai.csap.apidoc.annotation.ApiStatus.FINISH);
        JsonNode paths = convert(doc).get("paths");
        JsonNode list = paths.get("/api/user/list").get("get");
        assertTrue(list.get("deprecated").asBoolean());
        assertEquals("DEPRECATED", list.get("x-csap-status").asText());
        assertEquals("原说明\n\n请改用 /v2/list", list.get("description").asText());
        JsonNode save = paths.get("/api/user/save").get("post");
        assertFalse(save.has("deprecated"));
        assertEquals("FINISH", save.get("x-csap-status").asText());
        // 开发中(DEFAULT)什么都不输出
        JsonNode get = paths.get("/api/user/{id}").get("get");
        assertFalse(get.has("deprecated"));
        assertFalse(get.has("x-csap-status"));
    }

    @Test
    void nullRequiredDoesNotThrowAndIsFalse() throws Exception {
        JsonNode params = convert(doc()).get("paths").get("/api/user/list").get("get").get("parameters");
        assertEquals("name", params.get(0).get("name").asText());
        assertEquals("query", params.get(0).get("in").asText());
        assertFalse(params.get(0).get("required").asBoolean());
        assertTrue(params.get(1).get("required").asBoolean());
        assertEquals("integer", params.get(1).get("schema").get("type").asText());
    }

    @Test
    void pathParameterIsAlwaysRequired() throws Exception {
        JsonNode param = convert(doc()).get("paths").get("/api/user/{id}").get("get").get("parameters").get(0);
        assertEquals("path", param.get("in").asText());
        assertTrue(param.get("required").asBoolean());
    }

    @Test
    void bodyParametersBecomeJsonRequestBody() throws Exception {
        JsonNode post = convert(doc()).get("paths").get("/api/user/save").get("post");
        assertNull(post.get("parameters"));
        JsonNode schema = post.get("requestBody").get("content").get("application/json").get("schema");
        assertEquals("object", schema.get("type").asText());
        assertEquals("[\"username\",\"address\"]", schema.get("required").toString());
        JsonNode address = schema.get("properties").get("address");
        assertEquals("object", address.get("type").asText());
        assertEquals("string", address.get("properties").get("street").get("type").asText());
        assertNull(address.get("required"), "子字段 required 为 null 不应进入 required 列表");
        JsonNode tags = schema.get("properties").get("tags");
        assertEquals("array", tags.get("type").asText());
        assertEquals("string", tags.get("items").get("type").asText());
    }

    @Test
    void responseSchemaUsesResponseFields() throws Exception {
        JsonNode schema = convert(doc()).get("paths").get("/api/user/list").get("get").get("responses").get("200")
                                        .get("content").get("application/json").get("schema");
        assertEquals("integer", schema.get("properties").get("total").get("type").asText());
    }

    @Test
    void requestMappingWithSeveralMethodsGetsUniqueOperationIds() throws Exception {
        CsapDocResponse doc = doc();
        CsapDocMethod any = method("com.example.UserController.any", "/any", RequestMethod.GET, ParamType.DEFAULT, null, null);
        any.setMethods(new ArrayList<>(List.of(RequestMethod.GET, RequestMethod.POST)));
        doc.getApiList().get(0).getMethodList().add(any);
        JsonNode item = convert(doc).get("paths").get("/api/user/any");
        assertNotNull(item.get("get"));
        assertNotNull(item.get("post"));
        assertFalse(item.get("get").get("operationId").asText().equals(item.get("post").get("operationId").asText()));
    }

    @Test
    void globalApiListIsIncludedAndEmptyDocIsValid() throws Exception {
        CsapDocResponse doc = new CsapDocResponse();
        doc.setGlobalApiList(new ArrayList<>(doc().getApiList()));
        assertTrue(convert(doc).get("paths").has("/api/user/list"));

        JsonNode empty = convert(new CsapDocResponse());
        assertEquals(0, empty.get("paths").size());
        assertNotNull(empty.get("info").get("title"));
        assertNotNull(mapper.readTree(service.convertToOpenApi((CsapDocResponse) null)));
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyParentResponseOverloadStillWorks() throws Exception {
        CsapDocParentResponse parent = new CsapDocParentResponse();
        parent.setGlobalApiList(new ArrayList<>(doc().getApiList()));
        JsonNode root = mapper.readTree(service.convertToOpenApi(parent));
        assertTrue(root.get("paths").has("/api/user/list"));
    }

    @Test
    void specialCharactersAreEscaped() throws Exception {
        CsapDocResponse doc = doc();
        doc.getApiList().get(0).getMethodList().get(0).setDescription("含 \"引号\"\n换行 \\ 反斜杠");
        JsonNode op = convert(doc).get("paths").get("/api/user/list").get("get");
        assertEquals("含 \"引号\"\n换行 \\ 反斜杠", op.get("description").asText());
    }

    @Test
    void simplePathVariableParameterIsNotDuplicatedAsQuery() throws Exception {
        // 与扫描结果一致:@PathVariable Long id 的 paramType 是 DEFAULT(GET)或继承方法的 BODY(PUT)
        CsapDocResponse doc = doc();
        CsapDocModel product = new CsapDocModel();
        product.setName("com.example.Product");
        product.setModelType(ModelType.OBJECT);
        CsapDocModel idModel = model(null, param("id", ParamType.BODY, true, "Long"));
        idModel.setModelType(ModelType.BASE_DATA);
        CsapDocMethod update = method("com.example.UserController.update", "/update/{id}", RequestMethod.PUT, ParamType.BODY,
                List.of(idModel, product), null);
        CsapDocModel getId = model(null, param("id", ParamType.DEFAULT, true, "Long"));
        getId.setModelType(ModelType.BASE_DATA);
        CsapDocMethod detail = method("com.example.UserController.detail", "/detail/{id}", RequestMethod.GET, ParamType.DEFAULT,
                List.of(getId), null);
        doc.getApiList().get(0).getMethodList().add(update);
        doc.getApiList().get(0).getMethodList().add(detail);
        JsonNode paths = convert(doc).get("paths");

        JsonNode get = paths.get("/api/user/detail/{id}").get("get");
        assertEquals(1, get.get("parameters").size());
        assertEquals("path", get.get("parameters").get(0).get("in").asText());
        assertEquals("integer", get.get("parameters").get(0).get("schema").get("type").asText());

        JsonNode put = paths.get("/api/user/update/{id}").get("put");
        assertEquals(1, put.get("parameters").size());
        assertEquals("path", put.get("parameters").get(0).get("in").asText());
        JsonNode body = put.get("requestBody").get("content").get("application/json").get("schema");
        assertEquals("object", body.get("type").asText());
        assertNull(body.get("properties"), "id 已作为路径参数,不应再出现在请求体里");
    }

    @Test
    void objectBodyWithoutVisibleFieldsStillDeclaresRequestBody() throws Exception {
        CsapDocResponse doc = doc();
        CsapDocModel product = new CsapDocModel();
        product.setModelType(ModelType.OBJECT);
        doc.getApiList().get(0).getMethodList().add(
                method("com.example.UserController.create", "/create", RequestMethod.POST, ParamType.BODY, List.of(product), null));
        JsonNode post = convert(doc).get("paths").get("/api/user/create").get("post");
        assertTrue(post.get("requestBody").get("required").asBoolean());
        assertEquals("object", post.get("requestBody").get("content").get("application/json").get("schema").get("type").asText());
    }
}
