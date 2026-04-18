package ai.csap.validation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ApiToOpenApiConverterStream {
    public static final String PATH = "/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/csap-framework-validation-core/src/test/resources/";

    public static void main(String[] args) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            ApiListData apiListResponse = objectMapper.readValue(
                    new File(PATH + "api.json"), ApiListData.class);

            OpenAPI openAPI = buildOpenAPI(apiListResponse);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    new File(PATH + "openapi_stream.json"), openAPI);

            System.out.println("转换完成，已生成openapi_stream.json文件");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static OpenAPI buildOpenAPI(ApiListData apiListResponse) {
        OpenAPI openAPI = new OpenAPI();


        // 设置基本信息
        ApiInfo apiInfo = apiListResponse.getApiInfo();
        if (apiInfo != null) {
            openAPI.setInfo(new Info()
                    .title(apiInfo.getTitle())
                    .description(apiInfo.getDescription())
                    .version(apiInfo.getVersion()));

            // 只有当license相关信息不为空时才设置
            if (apiInfo.getLicense() != null || apiInfo.getLicenseUrl() != null) {
                openAPI.getInfo().license(new License()
                        .name(apiInfo.getLicense())
                        .url(apiInfo.getLicenseUrl()));
            }
        }

        // 设置服务器信息
        if (apiInfo != null && apiInfo.getServiceUrl() != null) {
            openAPI.setServers(Collections.singletonList(
                    new Server().url(apiInfo.getServiceUrl())));
        }

        // 处理安全方案
        if (apiInfo != null && apiInfo.getAuthorizationType() != null) {
            Optional.ofNullable(apiInfo.getAuthorizationType())
                    .filter(authType -> authType.startsWith("Authorization Bearer"))
                    .ifPresent(authType -> {
                        openAPI.setComponents(new Components()
                                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                        .type("http")
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
                        openAPI.setSecurity(Collections.singletonList(
                                new SecurityRequirement().addList("bearerAuth")));
                    });
        }

        // 处理所有API路径
        if (apiListResponse.getApiList() != null) {
            Map<String, PathItem> paths = apiListResponse.getApiList().stream()
                    .filter(Objects::nonNull) // 过滤掉null的ApiGroup
                    .flatMap(group -> group.getChildren() != null ? group.getChildren().stream() : Stream.empty())
                    .filter(Objects::nonNull) // 过滤掉null的ApiDetail
                    .collect(Collectors.groupingBy(ApiDetail::getPath))
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> createPathItem(entry.getValue())
                    ));

            openAPI.setPaths(new Paths());
            openAPI.getPaths().putAll(paths);
        }

        return openAPI;
    }

    private static PathItem createPathItem(List<ApiDetail> apis) {
        PathItem pathItem = new PathItem();

        if (apis == null || apis.isEmpty()) {
            return pathItem;
        }

        apis.forEach(api -> {
            // 空值检查
            if (api == null || api.getMethod() == null) {
                return;
            }

            Operation operation = new Operation()
                    .summary(api.getTitle() != null ? api.getTitle() : "未命名操作")
                    .description(api.getTitle() != null ? api.getTitle() : "")
                    .operationId(api.getKey() != null ? api.getKey() : "operation_" + System.currentTimeMillis());

            // 为所有操作添加默认响应
            operation.addResponsesItem("200", new Response()
                    .description("成功")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType()
                                    .schema(new Schema().type("object")))));

            // 根据HTTP方法类型设置操作
            switch (api.getMethod()) {
                case "GET":
                    pathItem.setGet(operation);
                    break;
                case "POST":
                    pathItem.setPost(operation);
                    break;
                case "PUT":
                    pathItem.setPut(operation);
                    break;
                case "DELETE":
                    pathItem.setDelete(operation);
                    break;
                default:
                    System.out.println("警告: 不支持的HTTP方法 " + api.getMethod());
                    break;
            }
        });

        return pathItem;
    }

    // 以下是所有需要的POJO类
    static class ApiListResponse {
        private String code;
        private ApiListData data;
        private String language;
        private String message;
        private boolean success;
        private long time;

        // getter和setter
        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public ApiListData getData() {
            return data;
        }

        public void setData(ApiListData data) {
            this.data = data;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }
    }

    static class ApiListData {
        private ApiInfo apiInfo;
        private List<ApiGroup> apiList;

        public ApiInfo getApiInfo() {
            return apiInfo;
        }

        public void setApiInfo(ApiInfo apiInfo) {
            this.apiInfo = apiInfo;
        }

        public List<ApiGroup> getApiList() {
            return apiList;
        }

        public void setApiList(List<ApiGroup> apiList) {
            this.apiList = apiList;
        }
    }

    static class ApiInfo {
        private String authorizationType;
        private String description;
        private String license;
        private String licenseUrl;
        private String serviceUrl;
        private String title;
        private String version;

        public String getAuthorizationType() {
            return authorizationType;
        }

        public void setAuthorizationType(String authorizationType) {
            this.authorizationType = authorizationType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getLicense() {
            return license;
        }

        public void setLicense(String license) {
            this.license = license;
        }

        public String getLicenseUrl() {
            return licenseUrl;
        }

        public void setLicenseUrl(String licenseUrl) {
            this.licenseUrl = licenseUrl;
        }

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    static class ApiGroup {
        private List<ApiDetail> children;
        private String key;
        private String title;

        public List<ApiDetail> getChildren() {
            return children;
        }

        public void setChildren(List<ApiDetail> children) {
            this.children = children;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    static class ApiDetail {
        private boolean isLeaf;
        private String key;
        private String method;
        private String path;
        private String title;
        private String type;

        public boolean isLeaf() {
            return isLeaf;
        }

        public void setLeaf(boolean leaf) {
            isLeaf = leaf;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    // OpenAPI模型类
    static class OpenAPI {
        private Info info;
        private List<Server> servers;
        private Paths paths;
        private Components components;
        private List<SecurityRequirement> security;

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

        public Paths getPaths() {
            return paths;
        }

        public void setPaths(Paths paths) {
            this.paths = paths;
        }

        public Components getComponents() {
            return components;
        }

        public void setComponents(Components components) {
            this.components = components;
        }

        public List<SecurityRequirement> getSecurity() {
            return security;
        }

        public void setSecurity(List<SecurityRequirement> security) {
            this.security = security;
        }
    }

    static class Info {
        private String title;
        private String description;
        private String version;
        private License license;

        public String getTitle() {
            return title;
        }

        public Info title(String title) {
            this.title = title;
            return this;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public Info description(String description) {
            this.description = description;
            return this;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public Info version(String version) {
            this.version = version;
            return this;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public License getLicense() {
            return license;
        }

        public Info license(License license) {
            this.license = license;
            return this;
        }

        public void setLicense(License license) {
            this.license = license;
        }
    }

    static class License {
        private String name;
        private String url;

        public String getName() {
            return name;
        }

        public License name(String name) {
            this.name = name;
            return this;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public License url(String url) {
            this.url = url;
            return this;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    static class Server {
        private String url;

        public String getUrl() {
            return url;
        }

        public Server url(String url) {
            this.url = url;
            return this;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    static class Paths extends HashMap<String, PathItem> implements Map<String, PathItem> {
    }

    static class PathItem {
        private Operation get;
        private Operation post;
        private Operation put;
        private Operation delete;

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
    }

    static class Operation {
        private String summary;
        private String description;
        private String operationId;
        private List<Parameter> parameters;
        private Map<String, Response> responses;

        public String getSummary() {
            return summary;
        }

        public Operation summary(String summary) {
            this.summary = summary;
            return this;
        }

        public String getDescription() {
            return description;
        }

        public Operation description(String description) {
            this.description = description;
            return this;
        }

        public String getOperationId() {
            return operationId;
        }

        public Operation operationId(String operationId) {
            this.operationId = operationId;
            return this;
        }

        public List<Parameter> getParameters() {
            if (parameters == null) parameters = new ArrayList<>();
            return parameters;
        }

        public Operation addParametersItem(Parameter parameter) {
            getParameters().add(parameter);
            return this;
        }

        public void setParameters(List<Parameter> parameters) {
            this.parameters = parameters;
        }

        public Map<String, Response> getResponses() {
            if (responses == null) responses = new HashMap<>();
            return responses;
        }

        public Operation addResponsesItem(String key, Response response) {
            getResponses().put(key, response);
            return this;
        }

        public void setResponses(Map<String, Response> responses) {
            this.responses = responses;
        }
    }

    static class Parameter {
        private String name;
        private String description;
        private boolean required;
        private String in;
        private Schema schema;

        public String getName() {
            return name;
        }

        public Parameter name(String name) {
            this.name = name;
            return this;
        }

        public String getDescription() {
            return description;
        }

        public Parameter description(String description) {
            this.description = description;
            return this;
        }

        public boolean isRequired() {
            return required;
        }

        public Parameter required(boolean required) {
            this.required = required;
            return this;
        }

        public String getIn() {
            return in;
        }

        public Parameter in(String in) {
            this.in = in;
            return this;
        }

        public Schema getSchema() {
            return schema;
        }

        public Parameter schema(Schema schema) {
            this.schema = schema;
            return this;
        }
    }

    static class Response {
        private String description;
        private Content content;

        public String getDescription() {
            return description;
        }

        public Response description(String description) {
            this.description = description;
            return this;
        }

        public Content getContent() {
            return content;
        }

        public Response content(Content content) {
            this.content = content;
            return this;
        }
    }

    static class Content extends HashMap<String, MediaType> {
        public Content addMediaType(String key, MediaType mediaType) {
            super.put(key, mediaType);
            return this;
        }
    }

    static class MediaType {
        private Schema schema;

        public Schema getSchema() {
            return schema;
        }

        public MediaType schema(Schema schema) {
            this.schema = schema;
            return this;
        }
    }

    static class Schema {
        private String type;
        private Map<String, Schema> properties;
        private String description;

        public String getType() {
            return type;
        }

        public Schema type(String type) {
            this.type = type;
            return this;
        }

        public Map<String, Schema> getProperties() {
            if (properties == null) properties = new HashMap<>();
            return properties;
        }

        public Schema description(String description) {
            this.description = description;
            return this;
        }
    }

    static class Components {
        private Map<String, SecurityScheme> securitySchemes;

        public Map<String, SecurityScheme> getSecuritySchemes() {
            if (securitySchemes == null) securitySchemes = new HashMap<>();
            return securitySchemes;
        }

        public Components addSecuritySchemes(String key, SecurityScheme scheme) {
            getSecuritySchemes().put(key, scheme);
            return this;
        }
    }

    static class SecurityScheme {
        private String type;
        private String scheme;
        private String bearerFormat;

        public String getType() {
            return type;
        }

        public SecurityScheme type(String type) {
            this.type = type;
            return this;
        }

        public String getScheme() {
            return scheme;
        }

        public SecurityScheme scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public String getBearerFormat() {
            return bearerFormat;
        }

        public SecurityScheme bearerFormat(String bearerFormat) {
            this.bearerFormat = bearerFormat;
            return this;
        }
    }

    static class SecurityRequirement extends HashMap<String, List<String>> {
        public SecurityRequirement addList(String key) {
            put(key, new ArrayList<>());
            return this;
        }
    }
}
