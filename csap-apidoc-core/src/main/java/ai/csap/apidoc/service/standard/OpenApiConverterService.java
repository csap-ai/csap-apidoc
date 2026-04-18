package ai.csap.apidoc.service.standard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.RequestMethod;

import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.response.CsapDocParentResponse;
import ai.csap.apidoc.type.ModelType;

/**
 * OpenAPI规范转换器服务
 * 将API文档转换为OpenAPI 3.0.0规范的JSON格式
 *
 * @author yangchengfu
 * @date 2024/12/01
 */
public class OpenApiConverterService {

    /**
     * 将API文档转换为OpenAPI格式
     *
     * @param apiDocResponse API文档响应
     * @return OpenAPI JSON字符串
     */
    public String convertToOpenApi(CsapDocParentResponse apiDocResponse) {
        try {
            OpenApiSpec openApiSpec = buildOpenApiSpec(apiDocResponse);
            return convertToJsonString(openApiSpec);
        } catch (Exception e) {
            System.err.println("转换OpenAPI规范失败: " + e.getMessage());
            throw new RuntimeException("转换OpenAPI规范失败", e);
        }
    }

    /**
     * 构建OpenAPI规范
     */
    private OpenApiSpec buildOpenApiSpec(CsapDocParentResponse apiDocResponse) {
        OpenApiSpec spec = new OpenApiSpec();

        // 设置OpenAPI版本
        spec.setOpenapi("3.0.0");

        // 设置基本信息
        spec.setInfo(buildInfo(apiDocResponse));

        // 设置服务器信息
        spec.setServers(buildServers());

        // 构建路径信息
        Map<String, PathItem> paths = new LinkedHashMap<>();

        // 处理全局API列表
        if (apiDocResponse.getGlobalApiList() != null) {
            for (CsapDocModelController controller : apiDocResponse.getGlobalApiList()) {
                buildControllerPaths(controller, paths);
            }
        }

        spec.setPaths(paths);

        // 构建组件（schemas, responses等）
        spec.setComponents(buildComponents(apiDocResponse));

        // 设置标签
        spec.setTags(buildTags(apiDocResponse));

        return spec;
    }

    /**
     * 构建基本信息
     */
    private Info buildInfo(CsapDocParentResponse apiDocResponse) {
        Info info = new Info();

        if (apiDocResponse.getApiInfo() != null) {
            info.setTitle(apiDocResponse.getApiInfo().getTitle());
            info.setDescription(apiDocResponse.getApiInfo().getDescription());
            info.setVersion(apiDocResponse.getApiInfo().getVersion());
        } else {
            info.setTitle("CSAP API Documentation");
            info.setDescription("Generated from CSAP Framework");
            info.setVersion("1.0.0");
        }

        Contact contact = new Contact();
        contact.setName("CSAP Framework");
        contact.setEmail("support@csap.com");
        info.setContact(contact);

        return info;
    }

    /**
     * 构建服务器信息
     */
    private List<Server> buildServers() {
        List<Server> servers = new ArrayList<>();

        Server localServer = new Server();
        localServer.setUrl("http://localhost:8080");
        localServer.setDescription("本地开发服务器");
        servers.add(localServer);

        Server prodServer = new Server();
        prodServer.setUrl("https://api.example.com");
        prodServer.setDescription("生产环境服务器");
        servers.add(prodServer);

        return servers;
    }

    /**
     * 构建控制器路径
     */
    private void buildControllerPaths(CsapDocModelController controller, Map<String, PathItem> paths) {
        if (controller.getMethodList() == null || controller.getMethodList().isEmpty()) {
            return;
        }

        for (CsapDocMethod method : controller.getMethodList()) {
            if (Boolean.TRUE.equals(method.getHidden())) {
                continue;
            }

            String path = buildPath(method, controller);
            PathItem pathItem = paths.computeIfAbsent(path, k -> new PathItem());

            Operation operation = buildOperation(method, controller);

            // 根据HTTP方法设置操作
            if (method.getMethods() != null) {
                for (RequestMethod requestMethod : method.getMethods()) {
                    switch (requestMethod) {
                        case GET:
                            pathItem.setGet(operation);
                            break;
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
                }
            } else {
                pathItem.setGet(operation);
            }
        }
    }

    /**
     * 构建路径
     */
    private String buildPath(CsapDocMethod method, CsapDocModelController controller) {
        StringBuilder path = new StringBuilder();

        // 添加控制器路径
        if (controller.getPath() != null && controller.getPath().length > 0) {
            String controllerPath = controller.getPath()[0];
            if (!controllerPath.startsWith("/")) {
                path.append("/");
            }
            path.append(controllerPath);
        }

        // 添加方法路径
        if (method.getPaths() != null && method.getPaths().length > 0) {
            String methodPath = method.getPaths()[0];
            if (!path.toString().endsWith("/") && !methodPath.startsWith("/")) {
                path.append("/");
            }
            path.append(methodPath);
        }

        // 确保路径以/开头
        if (path.length() == 0 || !path.toString().startsWith("/")) {
            path.insert(0, "/");
        }

        return path.toString();
    }

    /**
     * 构建操作
     */
    private Operation buildOperation(CsapDocMethod method, CsapDocModelController controller) {
        Operation operation = new Operation();

        operation.setSummary(method.getValue());
        operation.setDescription(method.getDescription());
        operation.setOperationId(method.getKey());

        // 设置标签
        List<String> tags = new ArrayList<>();
        if (controller.getValue() != null && !controller.getValue().isEmpty()) {
            tags.add(controller.getValue());
        } else if (controller.getSimpleName() != null) {
            tags.add(controller.getSimpleName());
        }
        operation.setTags(tags);

        // 构建参数
        List<Parameter> parameters = buildParameters(method);
        if (!parameters.isEmpty()) {
            operation.setParameters(parameters);
        }

        // 构建请求体
        if (method.getRequest() != null && !method.getRequest().isEmpty() &&
                method.getMethods() != null &&
                (method.getMethods().contains(RequestMethod.POST) ||
                        method.getMethods().contains(RequestMethod.PUT) ||
                        method.getMethods().contains(RequestMethod.PATCH))) {
            operation.setRequestBody(buildRequestBody(method));
        }

        // 构建响应
        operation.setResponses(buildResponses(method));

        return operation;
    }

    /**
     * 构建参数
     */
    private List<Parameter> buildParameters(CsapDocMethod method) {
        List<Parameter> parameters = new ArrayList<>();

        if (method.getRequest() != null) {
            for (CsapDocModel requestModel : method.getRequest()) {
                if (requestModel.getParameters() != null) {
                    for (CsapDocParameter param : requestModel.getParameters()) {
                        // 只处理查询参数和路径参数
                        if (param.getParamType() == ParamType.QUERY ||
                                param.getParamType() == ParamType.DEFAULT ||
                                param.getParamType() == ParamType.PATH) {

                            Parameter parameter = new Parameter();
                            parameter.setName(param.getName());
                            parameter.setDescription(param.getDescription());
                            parameter.setRequired(param.getRequired());

                            // 设置参数位置
                            if (param.getParamType() == ParamType.PATH) {
                                parameter.setIn("path");
                            } else {
                                parameter.setIn("query");
                            }

                            // 设置参数模式
                            Schema schema = new Schema();
                            schema.setType(mapDataTypeToOpenApiType(param.getDataType()));
                            schema.setDescription(param.getDescription());

                            if (param.getExample() != null && !param.getExample().isEmpty()) {
                                schema.setExample(param.getExample());
                            }

                            parameter.setSchema(schema);
                            parameters.add(parameter);
                        }
                    }
                }
            }
        }

        return parameters;
    }

    /**
     * 构建请求体
     */
    private RequestBody buildRequestBody(CsapDocMethod method) {
        RequestBody requestBody = new RequestBody();
        requestBody.setDescription("请求体参数");
        requestBody.setRequired(true);

        Map<String, MediaType> content = new HashMap<>();
        MediaType mediaType = new MediaType();

        Schema schema = buildSchemaFromModels(method.getRequest());
        mediaType.setSchema(schema);

        content.put("application/json", mediaType);
        requestBody.setContent(content);

        return requestBody;
    }

    /**
     * 构建响应
     */
    private Map<String, Response> buildResponses(CsapDocMethod method) {
        Map<String, Response> responses = new HashMap<>();

        // 200 成功响应
        Response successResponse = new Response();
        successResponse.setDescription("请求成功");

        if (method.getResponse() != null && !method.getResponse().isEmpty()) {
            Map<String, MediaType> content = new HashMap<>();
            MediaType mediaType = new MediaType();

            Schema responseSchema = buildResponseSchema(method.getResponse());
            mediaType.setSchema(responseSchema);

            content.put("application/json", mediaType);
            successResponse.setContent(content);
        }

        responses.put("200", successResponse);

        // 400 错误响应
        Response errorResponse = new Response();
        errorResponse.setDescription("请求参数错误");
        responses.put("400", errorResponse);

        // 404 错误响应
        Response notFoundResponse = new Response();
        notFoundResponse.setDescription("资源不存在");
        responses.put("404", notFoundResponse);

        return responses;
    }

    /**
     * 从模型构建Schema
     */
    private Schema buildSchemaFromModels(List<CsapDocModel> models) {
        if (models == null || models.isEmpty()) {
            return new Schema().type("object");
        }

        Schema schema = new Schema();
        schema.setType("object");
        Map<String, Schema> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (CsapDocModel model : models) {
            if (model.getParameters() != null) {
                for (CsapDocParameter param : model.getParameters()) {
                    if (Boolean.TRUE.equals(param.getHidden())) {
                        continue;
                    }

                    Schema propSchema = buildSchemaFromParameter(param);
                    properties.put(param.getName(), propSchema);

                    if (param.getRequired()) {
                        required.add(param.getName());
                    }
                }
            }
        }

        schema.setProperties(properties);
        if (!required.isEmpty()) {
            schema.setRequired(required);
        }

        return schema;
    }

    /**
     * 构建响应Schema
     */
    private Schema buildResponseSchema(List<CsapDocModel> models) {
        Schema wrapperSchema = new Schema();
        wrapperSchema.setType("object");
        Map<String, Schema> wrapperProperties = new LinkedHashMap<>();

        // 标准响应格式
        wrapperProperties.put("code", new Schema().type("string").description("响应码").example("0"));
        wrapperProperties.put("message", new Schema().type("string").description("响应消息").example("成功"));
        wrapperProperties.put("time", new Schema().type("integer").description("响应时间").example(1540483321460L));

        // 数据部分
        Schema dataSchema = buildSchemaFromModels(models);
        wrapperProperties.put("data", dataSchema);

        wrapperSchema.setProperties(wrapperProperties);
        return wrapperSchema;
    }

    /**
     * 从参数构建Schema
     */
    private Schema buildSchemaFromParameter(CsapDocParameter param) {
        Schema schema = new Schema();

        // 处理数组类型
        if (param.getModelType() == ModelType.ARRAY || isArrayType(param.getDataType())) {
            schema.setType("array");

            if (param.getParameters() != null && !param.getParameters().isEmpty()) {
                // 对象数组
                Schema itemSchema = new Schema();
                itemSchema.setType("object");
                Map<String, Schema> itemProperties = new LinkedHashMap<>();

                for (CsapDocParameter childParam : param.getParameters()) {
                    itemProperties.put(childParam.getName(), buildSchemaFromParameter(childParam));
                }

                itemSchema.setProperties(itemProperties);
                schema.setItems(itemSchema);
            } else {
                // 基础类型数组
                Schema itemSchema = new Schema();
                itemSchema.setType(mapDataTypeToOpenApiType(param.getDataType()));
                schema.setItems(itemSchema);
            }
        } else if (param.getParameters() != null && !param.getParameters().isEmpty()) {
            // 对象类型
            schema.setType("object");
            Map<String, Schema> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            for (CsapDocParameter childParam : param.getParameters()) {
                properties.put(childParam.getName(), buildSchemaFromParameter(childParam));
                if (childParam.getRequired()) {
                    required.add(childParam.getName());
                }
            }

            schema.setProperties(properties);
            if (!required.isEmpty()) {
                schema.setRequired(required);
            }
        } else {
            // 基础类型
            schema.setType(mapDataTypeToOpenApiType(param.getDataType()));
        }

        schema.setDescription(param.getDescription());

        if (param.getExample() != null && !param.getExample().isEmpty()) {
            schema.setExample(param.getExample());
        }

        return schema;
    }

    /**
     * 判断是否为数组类型
     */
    private boolean isArrayType(String dataType) {
        if (dataType == null) {
            return false;
        }
        String type = dataType.toLowerCase();
        return type.contains("[]") || type.contains("list<") || type.contains("set<") || type.contains("collection<");
    }

    /**
     * 映射数据类型到OpenAPI类型
     */
    private String mapDataTypeToOpenApiType(String dataType) {
        if (dataType == null || dataType.isEmpty()) {
            return "string";
        }

        String lowerType = dataType.toLowerCase();

        if (lowerType.contains("string")) {
            return "string";
        } else if (lowerType.contains("int") || lowerType.contains("long")) {
            return "integer";
        } else if (lowerType.contains("double") || lowerType.contains("float") || lowerType.contains("decimal")) {
            return "number";
        } else if (lowerType.contains("boolean")) {
            return "boolean";
        } else if (lowerType.contains("date") || lowerType.contains("time")) {
            return "string";
        } else if (lowerType.contains("[]") || lowerType.contains("list") || lowerType.contains("set")) {
            return "array";
        } else {
            return "string";
        }
    }

    /**
     * 构建组件
     */
    private Components buildComponents(CsapDocParentResponse apiDocResponse) {
        Components components = new Components();

        // 可以在这里添加通用的schemas, responses等
        Map<String, Schema> schemas = new HashMap<>();

        // 标准响应格式
        Schema standardResponse = new Schema();
        standardResponse.setType("object");
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

    /**
     * 构建标签
     */
    private List<Tag> buildTags(CsapDocParentResponse apiDocResponse) {
        List<Tag> tags = new ArrayList<>();
        Set<String> tagNames = new HashSet<>();

        if (apiDocResponse.getGlobalApiList() != null) {
            for (CsapDocModelController controller : apiDocResponse.getGlobalApiList()) {
                String tagName = controller.getValue() != null && !controller.getValue().isEmpty() ?
                        controller.getValue() : controller.getSimpleName();

                if (tagName != null && !tagNames.contains(tagName)) {
                    Tag tag = new Tag();
                    tag.setName(tagName);
                    tag.setDescription(controller.getDescription());
                    tags.add(tag);
                    tagNames.add(tagName);
                }
            }
        }

        return tags;
    }

    /**
     * 转换为JSON字符串
     */
    private String convertToJsonString(OpenApiSpec spec) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"openapi\": \"").append(spec.getOpenapi()).append("\",\n");
        json.append("  \"info\": ").append(convertToJsonString(spec.getInfo())).append(",\n");
        json.append("  \"servers\": ").append(convertToJsonString(spec.getServers())).append(",\n");
        json.append("  \"paths\": ").append(convertToJsonString(spec.getPaths())).append(",\n");
        json.append("  \"components\": ").append(convertToJsonString(spec.getComponents())).append(",\n");
        json.append("  \"tags\": ").append(convertToJsonString(spec.getTags())).append("\n");
        json.append("}");
        return json.toString();
    }

    private String convertToJsonString(Object obj) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof String) {
            return "\"" + escapeJsonString((String) obj) + "\"";
        } else if (obj instanceof Number) {
            return obj.toString();
        } else if (obj instanceof Boolean) {
            return obj.toString();
        } else if (obj instanceof Info) {
            Info info = (Info) obj;
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("    \"title\": ").append(convertToJsonString(info.getTitle())).append(",\n");
            json.append("    \"description\": ").append(convertToJsonString(info.getDescription())).append(",\n");
            json.append("    \"version\": ").append(convertToJsonString(info.getVersion()));
            if (info.getContact() != null) {
                json.append(",\n    \"contact\": ").append(convertToJsonString(info.getContact()));
            }
            json.append("\n  }");
            return json.toString();
        } else if (obj instanceof Contact) {
            Contact contact = (Contact) obj;
            return String.format("{\"name\":%s,\"email\":%s}",
                    convertToJsonString(contact.getName()), convertToJsonString(contact.getEmail()));
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.isEmpty()) {
                return "[]";
            }
            StringBuilder json = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    json.append(",\n");
                }
                String itemJson = convertToJsonString(list.get(i));
                json.append("    ").append(itemJson.replace("\n", "\n    "));
            }
            json.append("\n  ]");
            return json.toString();
        } else if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            if (map.isEmpty()) {
                return "{}";
            }
            StringBuilder json = new StringBuilder("{\n");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    json.append(",\n");
                }
                first = false;
                json.append("    ").append(convertToJsonString(entry.getKey())).append(": ");
                String valueJson = convertToJsonString(entry.getValue());
                json.append(valueJson.replace("\n", "\n    "));
            }
            json.append("\n  }");
            return json.toString();
        } else if (obj instanceof Server) {
            Server server = (Server) obj;
            return String.format("{\"url\":%s,\"description\":%s}",
                    convertToJsonString(server.getUrl()), convertToJsonString(server.getDescription()));
        } else if (obj instanceof PathItem) {
            PathItem pathItem = (PathItem) obj;
            StringBuilder json = new StringBuilder("{");
            boolean first = true;

            if (pathItem.getGet() != null) {
                json.append("\"get\":").append(convertToJsonString(pathItem.getGet()));
                first = false;
            }
            if (pathItem.getPost() != null) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"post\":").append(convertToJsonString(pathItem.getPost()));
                first = false;
            }
            if (pathItem.getPut() != null) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"put\":").append(convertToJsonString(pathItem.getPut()));
                first = false;
            }
            if (pathItem.getDelete() != null) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"delete\":").append(convertToJsonString(pathItem.getDelete()));
                first = false;
            }
            if (pathItem.getPatch() != null) {
                if (!first) {
                    json.append(",");
                }
                json.append("\"patch\":").append(convertToJsonString(pathItem.getPatch()));
            }

            json.append("}");
            return json.toString();
        } else if (obj instanceof Operation) {
            Operation op = (Operation) obj;
            StringBuilder json = new StringBuilder("{\n");
            json.append("      \"summary\": ").append(convertToJsonString(op.getSummary()));
            if (op.getDescription() != null && !op.getDescription().isEmpty()) {
                json.append(",\n      \"description\": ").append(convertToJsonString(op.getDescription()));
            }
            json.append(",\n      \"operationId\": ").append(convertToJsonString(op.getOperationId()));
            if (op.getTags() != null && !op.getTags().isEmpty()) {
                json.append(",\n      \"tags\": ").append(convertToJsonString(op.getTags()));
            }
            if (op.getParameters() != null && !op.getParameters().isEmpty()) {
                json.append(",\n      \"parameters\": ").append(convertToJsonString(op.getParameters()));
            }
            if (op.getRequestBody() != null) {
                json.append(",\n      \"requestBody\": ").append(convertToJsonString(op.getRequestBody()));
            }
            json.append(",\n      \"responses\": ").append(convertToJsonString(op.getResponses()));
            json.append("\n    }");
            return json.toString();
        } else if (obj instanceof Parameter) {
            Parameter param = (Parameter) obj;
            StringBuilder json = new StringBuilder("{");
            json.append("\"name\":").append(convertToJsonString(param.getName()));
            json.append(",\"in\":").append(convertToJsonString(param.getIn()));
            if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                json.append(",\"description\":").append(convertToJsonString(param.getDescription()));
            }
            json.append(",\"required\":").append(param.getRequired());
            json.append(",\"schema\":").append(convertToJsonString(param.getSchema()));
            json.append("}");
            return json.toString();
        } else if (obj instanceof RequestBody) {
            RequestBody rb = (RequestBody) obj;
            StringBuilder json = new StringBuilder("{");
            if (rb.getDescription() != null) {
                json.append("\"description\":").append(convertToJsonString(rb.getDescription())).append(",");
            }
            json.append("\"required\":").append(rb.getRequired());
            json.append(",\"content\":").append(convertToJsonString(rb.getContent()));
            json.append("}");
            return json.toString();
        } else if (obj instanceof Response) {
            Response resp = (Response) obj;
            StringBuilder json = new StringBuilder("{");
            json.append("\"description\":").append(convertToJsonString(resp.getDescription()));
            if (resp.getContent() != null && !resp.getContent().isEmpty()) {
                json.append(",\"content\":").append(convertToJsonString(resp.getContent()));
            }
            json.append("}");
            return json.toString();
        } else if (obj instanceof MediaType) {
            MediaType mt = (MediaType) obj;
            return "{\"schema\":" + convertToJsonString(mt.getSchema()) + "}";
        } else if (obj instanceof Schema) {
            Schema schema = (Schema) obj;
            StringBuilder json = new StringBuilder("{");
            json.append("\"type\":").append(convertToJsonString(schema.getType()));
            if (schema.getDescription() != null && !schema.getDescription().isEmpty()) {
                json.append(",\"description\":").append(convertToJsonString(schema.getDescription()));
            }
            if (schema.getExample() != null) {
                json.append(",\"example\":").append(convertToJsonString(schema.getExample()));
            }
            if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
                json.append(",\"properties\":").append(convertToJsonString(schema.getProperties()));
            }
            if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
                json.append(",\"required\":").append(convertToJsonString(schema.getRequired()));
            }
            if (schema.getItems() != null) {
                json.append(",\"items\":").append(convertToJsonString(schema.getItems()));
            }
            json.append("}");
            return json.toString();
        } else if (obj instanceof Components) {
            Components comp = (Components) obj;
            StringBuilder json = new StringBuilder("{");
            if (comp.getSchemas() != null && !comp.getSchemas().isEmpty()) {
                json.append("\"schemas\":").append(convertToJsonString(comp.getSchemas()));
            }
            json.append("}");
            return json.toString();
        } else if (obj instanceof Tag) {
            Tag tag = (Tag) obj;
            StringBuilder json = new StringBuilder("{");
            json.append("\"name\":").append(convertToJsonString(tag.getName()));
            if (tag.getDescription() != null && !tag.getDescription().isEmpty()) {
                json.append(",\"description\":").append(convertToJsonString(tag.getDescription()));
            }
            json.append("}");
            return json.toString();
        }

        return "{}";
    }

    private String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
