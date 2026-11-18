package ai.csap.apidoc.service.standard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocMethodHeaders;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.properties.CsapApiInfo;
import ai.csap.apidoc.response.CsapDocParentResponse;
import ai.csap.apidoc.type.ModelType;

/**
 * OpenAPI规范转换器服务
 * 将API文档转换为OpenAPI 3.0.3规范的JSON格式
 *
 * <p>入口是 {@link #convertToOpenApi(CsapDocResponse)}:同时处理 {@code globalApiList} 与正常扫描出的 {@code apiList}。
 * {@code GET /csap/openapi.json} 即由它生成。</p>
 *
 * @author yangchengfu
 * @date 2024/12/01
 */
public class OpenApiConverterService {

    /**
     * OpenAPI 版本
     */
    public static final String OPENAPI_VERSION = "3.0.3";

    /**
     * 嵌套模型最大展开深度,防止自引用模型无限递归
     */
    private static final int MAX_DEPTH = 12;

    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([^}:]+)(?::[^}]*)?}");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 将扫描得到的API文档转换为OpenAPI JSON
     *
     * @param apiDocResponse 文档数据({@code /csap/apidoc} 的返回内容)
     * @return OpenAPI JSON字符串
     */
    public String convertToOpenApi(CsapDocResponse apiDocResponse) {
        return toJson(buildOpenApiSpec(apiDocResponse == null ? null : apiDocResponse.getApiInfo(),
                apiDocResponse == null ? null : apiDocResponse.getGlobalApiList(),
                apiDocResponse == null ? null : apiDocResponse.getApiList()));
    }

    /**
     * 将扫描得到的API文档转换为OpenAPI结构对象
     *
     * @param apiDocResponse 文档数据
     * @return OpenAPI结构
     */
    public OpenApiSpec convertToOpenApiSpec(CsapDocResponse apiDocResponse) {
        return buildOpenApiSpec(apiDocResponse == null ? null : apiDocResponse.getApiInfo(),
                apiDocResponse == null ? null : apiDocResponse.getGlobalApiList(),
                apiDocResponse == null ? null : apiDocResponse.getApiList());
    }

    /**
     * 将树形文档转换为OpenAPI格式。树形结构里的普通接口只有标题和路径,没有参数信息,
     * 因此这里只能处理 {@code globalApiList}。
     *
     * @param apiDocResponse API文档响应
     * @return OpenAPI JSON字符串
     * @deprecated 请改用 {@link #convertToOpenApi(CsapDocResponse)}
     */
    @Deprecated
    public String convertToOpenApi(CsapDocParentResponse apiDocResponse) {
        return toJson(buildOpenApiSpec(apiDocResponse == null ? null : apiDocResponse.getApiInfo(),
                apiDocResponse == null ? null : apiDocResponse.getGlobalApiList(), null));
    }

    private String toJson(OpenApiSpec spec) {
        try {
            return MAPPER.writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("转换OpenAPI规范失败", e);
        }
    }

    /**
     * 构建OpenAPI规范
     */
    private OpenApiSpec buildOpenApiSpec(CsapApiInfo apiInfo,
                                         List<CsapDocModelController> globalApiList,
                                         List<CsapDocModelController> apiList) {
        OpenApiSpec spec = new OpenApiSpec();
        spec.setOpenapi(OPENAPI_VERSION);
        spec.setInfo(buildInfo(apiInfo));
        spec.setServers(buildServers());

        List<CsapDocModelController> controllers = new ArrayList<>();
        if (globalApiList != null) {
            controllers.addAll(globalApiList);
        }
        if (apiList != null) {
            controllers.addAll(apiList);
        }

        Map<String, PathItem> paths = new LinkedHashMap<>();
        Map<String, Tag> tags = new LinkedHashMap<>();
        Set<String> operationIds = new LinkedHashSet<>();
        for (CsapDocModelController controller : controllers) {
            if (controller == null || Boolean.TRUE.equals(controller.getHidden())) {
                continue;
            }
            if (buildControllerPaths(controller, paths, operationIds)) {
                String tagName = tagName(controller);
                if (tagName != null && !tags.containsKey(tagName)) {
                    Tag tag = new Tag();
                    tag.setName(tagName);
                    tag.setDescription(emptyToNull(controller.getDescription()));
                    tags.put(tagName, tag);
                }
            }
        }
        spec.setPaths(paths);
        spec.setComponents(buildComponents());
        spec.setTags(new ArrayList<>(tags.values()));
        return spec;
    }

    /**
     * 构建基本信息
     */
    private Info buildInfo(CsapApiInfo apiInfo) {
        Info info = new Info();
        info.setTitle(apiInfo != null && notEmpty(apiInfo.getTitle()) ? apiInfo.getTitle() : "CSAP API Documentation");
        info.setDescription(apiInfo != null ? emptyToNull(apiInfo.getDescription()) : null);
        info.setVersion(apiInfo != null && notEmpty(apiInfo.getVersion()) ? apiInfo.getVersion() : "1.0.0");
        return info;
    }

    /**
     * 构建服务器信息:使用相对地址,导入工具会以文档所在的服务为准
     */
    private List<Server> buildServers() {
        Server server = new Server();
        server.setUrl("/");
        server.setDescription("当前服务");
        return Collections.singletonList(server);
    }

    private String tagName(CsapDocModelController controller) {
        if (notEmpty(controller.getValue())) {
            return controller.getValue();
        }
        return notEmpty(controller.getSimpleName()) ? controller.getSimpleName() : controller.getName();
    }

    /**
     * 构建控制器路径
     *
     * @return 是否生成了至少一个操作
     */
    private boolean buildControllerPaths(CsapDocModelController controller, Map<String, PathItem> paths, Set<String> operationIds) {
        if (controller.getMethodList() == null || controller.getMethodList().isEmpty()) {
            return false;
        }
        boolean any = false;
        for (CsapDocMethod method : controller.getMethodList()) {
            if (method == null || Boolean.TRUE.equals(method.getHidden())) {
                continue;
            }
            String path = buildPath(method, controller);
            PathItem pathItem = paths.computeIfAbsent(path, k -> new PathItem());
            List<RequestMethod> requestMethods = method.getMethods() == null || method.getMethods().isEmpty()
                    ? Collections.singletonList(RequestMethod.GET) : method.getMethods();
            for (RequestMethod requestMethod : requestMethods) {
                Operation operation = buildOperation(method, controller, path, requestMethod);
                operation.setOperationId(uniqueOperationId(method, controller, requestMethod, operationIds));
                switch (requestMethod) {
                    case POST:
                        pathItem.setPost(operation);
                        break;
                    case PUT:
                        pathItem.setPut(operation);
                        break;
                    case DELETE:
                        pathItem.setDelete(operation);
                        break;
                    case PATCH:
                        pathItem.setPatch(operation);
                        break;
                    default:
                        pathItem.setGet(operation);
                        break;
                }
                any = true;
            }
        }
        return any;
    }

    private String uniqueOperationId(CsapDocMethod method, CsapDocModelController controller,
                                     RequestMethod requestMethod, Set<String> operationIds) {
        String base = notEmpty(method.getKey()) ? method.getKey()
                : (notEmpty(controller.getName()) ? controller.getName() + "." : "") + method.getName();
        String id = base;
        if (operationIds.contains(id)) {
            id = base + "_" + requestMethod.name().toLowerCase();
        }
        int i = 2;
        String candidate = id;
        while (operationIds.contains(candidate)) {
            candidate = id + "_" + i++;
        }
        operationIds.add(candidate);
        return candidate;
    }

    /**
     * 构建路径:控制器路径 + 方法路径,中间只保留一个 /;去掉路径变量里的正则
     */
    String buildPath(CsapDocMethod method, CsapDocModelController controller) {
        String base = null;
        if (controller.getPath() != null && controller.getPath().length > 0) {
            base = controller.getPath()[0];
        } else if (method.getApiPath() != null && method.getApiPath().length > 0) {
            base = method.getApiPath()[0];
        }
        String sub = method.getPaths() != null && method.getPaths().length > 0 ? method.getPaths()[0] : null;
        StringBuilder path = new StringBuilder();
        for (String part : new String[]{base, sub}) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            while (path.length() > 0 && path.charAt(path.length() - 1) == '/') {
                path.setLength(path.length() - 1);
            }
            path.append('/').append(part.trim().replaceFirst("^/+", ""));
        }
        String result = path.length() == 0 ? "/" : path.toString();
        if (result.length() > 1 && result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return PATH_VARIABLE.matcher(result).replaceAll("{$1}");
    }

    /**
     * 接口状态:过时 → deprecated: true;非"开发中"的状态输出 x-csap-status;有备注时追加到说明末尾
     */
    private void applyStatus(Operation operation, CsapDocMethod method) {
        ApiStatus status = method.getStatus();
        if (status == null || status == ApiStatus.DEFAULT) {
            return;
        }
        if (status == ApiStatus.DEPRECATED) {
            operation.setDeprecated(Boolean.TRUE);
        }
        operation.setCsapStatus(status.name());
        if (notEmpty(method.getStatusNote())) {
            String d = operation.getDescription();
            operation.setDescription((d == null || d.isEmpty() ? "" : d + "\n\n") + method.getStatusNote());
        }
    }

    /**
     * 构建操作
     */
    private Operation buildOperation(CsapDocMethod method, CsapDocModelController controller, String path, RequestMethod requestMethod) {
        Operation operation = new Operation();
        operation.setSummary(notEmpty(method.getValue()) ? method.getValue() : method.getName());
        operation.setDescription(emptyToNull(method.getDescription()));
        applyStatus(operation, method);
        String tagName = tagName(controller);
        if (tagName != null) {
            operation.setTags(Collections.singletonList(tagName));
        }

        boolean bodyAllowed = requestMethod == RequestMethod.POST || requestMethod == RequestMethod.PUT || requestMethod == RequestMethod.PATCH;
        List<Parameter> parameters = new ArrayList<>();
        Set<String> declared = new LinkedHashSet<>();
        List<CsapDocParameter> jsonBody = new ArrayList<>();
        List<CsapDocParameter> formBody = new ArrayList<>();
        boolean jsonBodyModel = false;
        Set<String> pathVariables = new LinkedHashSet<>();
        Matcher variables = PATH_VARIABLE.matcher(path);
        while (variables.find()) {
            pathVariables.add(variables.group(1));
        }

        if (method.getRequest() != null) {
            for (CsapDocModel model : method.getRequest()) {
                if (model == null) {
                    continue;
                }
                boolean simple = model.getModelType() == null || model.getModelType() == ModelType.BASE_DATA;
                if (model.getParameters() == null || model.getParameters().isEmpty()) {
                    // 对象类型的 @RequestBody 参数即使没有可展示的字段,也要声明请求体
                    if (bodyAllowed && !simple && resolveModelParamType(model, method) == ParamType.BODY) {
                        jsonBodyModel = true;
                    }
                    continue;
                }
                for (CsapDocParameter param : model.getParameters()) {
                    if (param == null || Boolean.TRUE.equals(param.getHidden()) || param.getName() == null) {
                        continue;
                    }
                    ParamType type = resolveParamType(param, model, method);
                    // 基础类型的方法参数(如 @PathVariable Long id)名字出现在路径变量里时,按路径参数处理
                    if (simple && type != ParamType.HEADER && pathVariables.contains(param.getName())) {
                        type = ParamType.PATH;
                    }
                    switch (type) {
                        case PATH:
                            addParameter(parameters, declared, param, "path");
                            break;
                        case HEADER:
                            addParameter(parameters, declared, param, "header");
                            break;
                        case BODY:
                            if (bodyAllowed) {
                                jsonBody.add(param);
                            } else {
                                addParameter(parameters, declared, param, "query");
                            }
                            break;
                        case FORM:
                        case FORM_DATA:
                            if (bodyAllowed) {
                                formBody.add(param);
                            } else {
                                addParameter(parameters, declared, param, "query");
                            }
                            break;
                        default:
                            addParameter(parameters, declared, param, "query");
                            break;
                    }
                }
            }
        }

        // 方法级请求头
        if (method.getMethodHeaders() != null) {
            for (CsapDocMethodHeaders header : method.getMethodHeaders()) {
                if (header == null || Boolean.TRUE.equals(header.getHidden()) || !notEmpty(header.getKey())
                        || declared.contains("header:" + header.getKey())) {
                    continue;
                }
                Parameter parameter = new Parameter();
                parameter.setName(header.getKey());
                parameter.setIn("header");
                parameter.setDescription(emptyToNull(notEmpty(header.getDescription()) ? header.getDescription() : header.getValue()));
                parameter.setRequired(Boolean.TRUE.equals(header.getRequired()));
                Schema schema = new Schema().type("string");
                if (notEmpty(header.getExample())) {
                    schema.setExample(header.getExample());
                }
                parameter.setSchema(schema);
                parameters.add(parameter);
                declared.add("header:" + header.getKey());
            }
        }

        // 路径里出现但没有声明的变量,补一个 string 类型的路径参数(OpenAPI 要求路径变量必须声明)
        for (String name : pathVariables) {
            if (!declared.contains("path:" + name)) {
                Parameter parameter = new Parameter();
                parameter.setName(name);
                parameter.setIn("path");
                parameter.setRequired(Boolean.TRUE);
                parameter.setSchema(new Schema().type("string"));
                parameters.add(parameter);
                declared.add("path:" + name);
            }
        }
        if (!parameters.isEmpty()) {
            operation.setParameters(parameters);
        }

        if (!jsonBody.isEmpty() || !formBody.isEmpty() || jsonBodyModel) {
            RequestBody requestBody = new RequestBody();
            Map<String, MediaType> content = new LinkedHashMap<>();
            boolean required = false;
            if (!jsonBody.isEmpty() || jsonBodyModel) {
                MediaType mediaType = new MediaType();
                mediaType.setSchema(buildObjectSchema(jsonBody, 0));
                content.put("application/json", mediaType);
                required = true;
            }
            if (!formBody.isEmpty()) {
                MediaType mediaType = new MediaType();
                mediaType.setSchema(buildObjectSchema(formBody, 0));
                content.put("application/x-www-form-urlencoded", mediaType);
                required = required || formBody.stream().anyMatch(p -> Boolean.TRUE.equals(p.getRequired()));
            }
            requestBody.setRequired(required);
            requestBody.setContent(content);
            operation.setRequestBody(requestBody);
        }

        operation.setResponses(buildResponses(method));
        return operation;
    }

    private ParamType resolveModelParamType(CsapDocModel model, CsapDocMethod method) {
        if (model.getParamType() != null && model.getParamType() != ParamType.DEFAULT) {
            return model.getParamType();
        }
        return method.getParamType() != null ? method.getParamType() : ParamType.DEFAULT;
    }

    /**
     * 参数位置:参数自身 &gt; 所属模型 &gt; 方法;DEFAULT 视为未指定
     */
    private ParamType resolveParamType(CsapDocParameter param, CsapDocModel model, CsapDocMethod method) {
        if (param.getParamType() != null && param.getParamType() != ParamType.DEFAULT) {
            return param.getParamType();
        }
        if (model.getParamType() != null && model.getParamType() != ParamType.DEFAULT) {
            return model.getParamType();
        }
        if (method.getParamType() != null) {
            return method.getParamType();
        }
        return ParamType.DEFAULT;
    }

    private void addParameter(List<Parameter> parameters, Set<String> declared, CsapDocParameter param, String in) {
        String id = in + ":" + param.getName();
        if (declared.contains(id)) {
            return;
        }
        Parameter parameter = new Parameter();
        parameter.setName(param.getName());
        parameter.setIn(in);
        parameter.setDescription(emptyToNull(param.getDescription()));
        // 路径参数在 OpenAPI 中必须为 required: true;其余按文档取值,null 视为非必填
        parameter.setRequired("path".equals(in) || Boolean.TRUE.equals(param.getRequired()));
        Schema schema = buildSchemaFromParameter(param, 0);
        schema.setDescription(null);
        parameter.setSchema(schema);
        parameters.add(parameter);
        declared.add(id);
    }

    /**
     * 构建响应
     */
    private Map<String, Response> buildResponses(CsapDocMethod method) {
        Map<String, Response> responses = new LinkedHashMap<>();
        Response successResponse = new Response();
        successResponse.setDescription("请求成功");
        List<CsapDocParameter> fields = new ArrayList<>();
        if (method.getResponse() != null) {
            for (CsapDocModel model : method.getResponse()) {
                if (model != null && model.getParameters() != null) {
                    fields.addAll(model.getParameters());
                }
            }
        }
        if (!fields.isEmpty()) {
            Map<String, MediaType> content = new LinkedHashMap<>();
            MediaType mediaType = new MediaType();
            mediaType.setSchema(buildObjectSchema(fields, 0));
            content.put("application/json", mediaType);
            successResponse.setContent(content);
        }
        responses.put("200", successResponse);
        return responses;
    }

    /**
     * 由一组字段构建 object Schema
     */
    private Schema buildObjectSchema(List<CsapDocParameter> fields, int depth) {
        Schema schema = new Schema().type("object");
        Map<String, Schema> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (CsapDocParameter field : fields) {
            if (field == null || Boolean.TRUE.equals(field.getHidden()) || field.getName() == null) {
                continue;
            }
            properties.put(field.getName(), buildSchemaFromParameter(field, depth + 1));
            if (Boolean.TRUE.equals(field.getRequired()) && !required.contains(field.getName())) {
                required.add(field.getName());
            }
        }
        if (!properties.isEmpty()) {
            schema.setProperties(properties);
        }
        if (!required.isEmpty()) {
            schema.setRequired(required);
        }
        return schema;
    }

    /**
     * 从参数构建Schema
     */
    private Schema buildSchemaFromParameter(CsapDocParameter param, int depth) {
        Schema schema;
        List<CsapDocParameter> children = depth < MAX_DEPTH ? param.getParameters() : null;
        boolean hasChildren = children != null && !children.isEmpty();
        if (param.getModelType() == ModelType.ARRAY || isArrayType(param.getDataType())) {
            schema = new Schema().type("array");
            schema.setItems(hasChildren ? buildObjectSchema(children, depth) : new Schema().type(mapDataTypeToOpenApiType(elementType(param.getDataType()))));
        } else if (hasChildren) {
            schema = buildObjectSchema(children, depth);
        } else if (param.getModelType() == ModelType.OBJECT || param.getModelType() == ModelType.T_OBJECT) {
            schema = new Schema().type("object");
        } else {
            schema = new Schema().type(mapDataTypeToOpenApiType(param.getDataType()));
        }
        schema.setDescription(emptyToNull(param.getDescription()));
        if (notEmpty(param.getExample())) {
            schema.setExample(param.getExample());
        }
        return schema;
    }

    private String elementType(String dataType) {
        if (dataType == null) {
            return null;
        }
        int lt = dataType.indexOf('<');
        int gt = dataType.lastIndexOf('>');
        if (lt >= 0 && gt > lt) {
            return dataType.substring(lt + 1, gt);
        }
        return dataType.replace("[]", "");
    }

    /**
     * 判断是否为数组类型
     */
    private boolean isArrayType(String dataType) {
        if (dataType == null) {
            return false;
        }
        String type = dataType.toLowerCase();
        return type.contains("[]") || type.contains("list<") || type.contains("set<") || type.contains("collection<")
                || "list".equals(type) || "set".equals(type) || "array".equals(type);
    }

    /**
     * 映射数据类型到OpenAPI类型
     */
    private String mapDataTypeToOpenApiType(String dataType) {
        if (dataType == null || dataType.isEmpty()) {
            return "string";
        }
        String lowerType = dataType.toLowerCase();
        if (lowerType.contains("string") || lowerType.contains("char")) {
            return "string";
        } else if (lowerType.contains("boolean")) {
            return "boolean";
        } else if (lowerType.contains("double") || lowerType.contains("float") || lowerType.contains("decimal")) {
            return "number";
        } else if (lowerType.contains("int") || lowerType.contains("long") || lowerType.contains("short")
                || lowerType.contains("byte") || lowerType.equals("biginteger")) {
            return "integer";
        } else if (lowerType.contains("map") || lowerType.equals("object")) {
            return "object";
        }
        return "string";
    }

    /**
     * 构建组件
     */
    private Components buildComponents() {
        Components components = new Components();
        Map<String, Schema> schemas = new LinkedHashMap<>();
        Schema standardResponse = new Schema().type("object");
        Map<String, Schema> responseProps = new LinkedHashMap<>();
        responseProps.put("code", new Schema().type("string").description("响应码"));
        responseProps.put("message", new Schema().type("string").description("响应消息"));
        responseProps.put("data", new Schema().description("响应数据"));
        responseProps.put("time", new Schema().type("integer").description("响应时间戳"));
        standardResponse.setProperties(responseProps);
        schemas.put("StandardResponse", standardResponse);
        components.setSchemas(schemas);
        return components;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String emptyToNull(String s) {
        return notEmpty(s) ? s : null;
    }

    // OpenAPI规范相关的模型类
    public static class OpenApiSpec {
        private String openapi;
        private Info info;
        private List<Server> servers;
        private Map<String, PathItem> paths;
        private Components components;
        private List<Tag> tags;

        // Getters and Setters
        public String getOpenapi() {
            return openapi;
        }

        public void setOpenapi(String openapi) {
            this.openapi = openapi;
        }

        public Info getInfo() {
            return info;
        }

        public void setInfo(Info info) {
            this.info = info;
        }

        public List<Server> getServers() {
            return servers;
        }

        public void setServers(List<Server> servers) {
            this.servers = servers;
        }

        public Map<String, PathItem> getPaths() {
            return paths;
        }

        public void setPaths(Map<String, PathItem> paths) {
            this.paths = paths;
        }

        public Components getComponents() {
            return components;
        }

        public void setComponents(Components components) {
            this.components = components;
        }

        public List<Tag> getTags() {
            return tags;
        }

        public void setTags(List<Tag> tags) {
            this.tags = tags;
        }
    }

    public static class Info {
        private String title;
        private String description;
        private String version;
        private Contact contact;

        // Getters and Setters
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public Contact getContact() {
            return contact;
        }

        public void setContact(Contact contact) {
            this.contact = contact;
        }
    }

    public static class Contact {
        private String name;
        private String email;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class Server {
        private String url;
        private String description;

        // Getters and Setters
        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class PathItem {
        private Operation get;
        private Operation post;
        private Operation put;
        private Operation delete;
        private Operation patch;

        // Getters and Setters
        public Operation getGet() {
            return get;
        }

        public void setGet(Operation get) {
            this.get = get;
        }

        public Operation getPost() {
            return post;
        }

        public void setPost(Operation post) {
            this.post = post;
        }

        public Operation getPut() {
            return put;
        }

        public void setPut(Operation put) {
            this.put = put;
        }

        public Operation getDelete() {
            return delete;
        }

        public void setDelete(Operation delete) {
            this.delete = delete;
        }

        public Operation getPatch() {
            return patch;
        }

        public void setPatch(Operation patch) {
            this.patch = patch;
        }
    }

    public static class Operation {
        private String summary;
        private String description;
        private String operationId;
        private List<String> tags;
        private List<Parameter> parameters;
        private RequestBody requestBody;
        private Map<String, Response> responses;
        private Boolean deprecated;
        private String csapStatus;

        public Boolean getDeprecated() {
            return deprecated;
        }

        public void setDeprecated(Boolean deprecated) {
            this.deprecated = deprecated;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("x-csap-status")
        public String getCsapStatus() {
            return csapStatus;
        }

        public void setCsapStatus(String csapStatus) {
            this.csapStatus = csapStatus;
        }

        // Getters and Setters
        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getOperationId() {
            return operationId;
        }

        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }

        public List<Parameter> getParameters() {
            return parameters;
        }

        public void setParameters(List<Parameter> parameters) {
            this.parameters = parameters;
        }

        public RequestBody getRequestBody() {
            return requestBody;
        }

        public void setRequestBody(RequestBody requestBody) {
            this.requestBody = requestBody;
        }

        public Map<String, Response> getResponses() {
            return responses;
        }

        public void setResponses(Map<String, Response> responses) {
            this.responses = responses;
        }
    }

    public static class Parameter {
        private String name;
        private String in;
        private String description;
        private Boolean required;
        private Schema schema;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIn() {
            return in;
        }

        public void setIn(String in) {
            this.in = in;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }

        public Schema getSchema() {
            return schema;
        }

        public void setSchema(Schema schema) {
            this.schema = schema;
        }
    }

    public static class RequestBody {
        private String description;
        private Boolean required;
        private Map<String, MediaType> content;

        // Getters and Setters
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getRequired() {
            return required;
        }

        public void setRequired(Boolean required) {
            this.required = required;
        }

        public Map<String, MediaType> getContent() {
            return content;
        }

        public void setContent(Map<String, MediaType> content) {
            this.content = content;
        }
    }

    public static class Response {
        private String description;
        private Map<String, MediaType> content;

        // Getters and Setters
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, MediaType> getContent() {
            return content;
        }

        public void setContent(Map<String, MediaType> content) {
            this.content = content;
        }
    }

    public static class MediaType {
        private Schema schema;

        // Getters and Setters
        public Schema getSchema() {
            return schema;
        }

        public void setSchema(Schema schema) {
            this.schema = schema;
        }
    }

    public static class Schema {
        private String type;
        private String description;
        private Object example;
        private Map<String, Schema> properties;
        private List<String> required;
        private Schema items;

        public Schema type(String type) {
            this.type = type;
            return this;
        }

        public Schema description(String description) {
            this.description = description;
            return this;
        }

        public Schema example(Object example) {
            this.example = example;
            return this;
        }

        // Getters and Setters
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Object getExample() {
            return example;
        }

        public void setExample(Object example) {
            this.example = example;
        }

        public Map<String, Schema> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Schema> properties) {
            this.properties = properties;
        }

        public List<String> getRequired() {
            return required;
        }

        public void setRequired(List<String> required) {
            this.required = required;
        }

        public Schema getItems() {
            return items;
        }

        public void setItems(Schema items) {
            this.items = items;
        }
    }

    public static class Components {
        private Map<String, Schema> schemas;

        // Getters and Setters
        public Map<String, Schema> getSchemas() {
            return schemas;
        }

        public void setSchemas(Map<String, Schema> schemas) {
            this.schemas = schemas;
        }
    }

    public static class Tag {
        private String name;
        private String description;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
