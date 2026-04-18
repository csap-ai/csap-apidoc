package ai.csap.validation;

import java.util.*;

/**
 * API和OpenAPI模型类
 */
public class ApiToOpenApiConverterModels {

    // API模型类
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
        private List<MethodInfo> methodList;
        private String description;
        private List<String> group;
        private boolean hidden;

        public List<MethodInfo> getMethodList() {
            return methodList;
        }

        public void setMethodList(List<MethodInfo> methodList) {
            this.methodList = methodList;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getGroup() {
            return group;
        }

        public void setGroup(List<String> group) {
            this.group = group;
        }

        public boolean isHidden() {
            return hidden;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }
    }

    static class MethodInfo {
        private List<String> apiPath;
        private String className;
        private String description;
        private List<String> group;
        private boolean hidden;
        private String key;
        private List<MethodHeader> methodHeaders;
        private List<String> methods;
        private String name;
        private List<String> paramNames;
        private String paramType;
        private List<String> paramTypes;
        private List<String> paths;
        private List<RequestParam> request;
        private List<ResponseParam> response;

        public List<String> getApiPath() {
            return apiPath;
        }

        public void setApiPath(List<String> apiPath) {
            this.apiPath = apiPath;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getGroup() {
            return group;
        }

        public void setGroup(List<String> group) {
            this.group = group;
        }

        public boolean isHidden() {
            return hidden;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public List<MethodHeader> getMethodHeaders() {
            return methodHeaders;
        }

        public void setMethodHeaders(List<MethodHeader> methodHeaders) {
            this.methodHeaders = methodHeaders;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getParamNames() {
            return paramNames;
        }

        public void setParamNames(List<String> paramNames) {
            this.paramNames = paramNames;
        }

        public String getParamType() {
            return paramType;
        }

        public void setParamType(String paramType) {
            this.paramType = paramType;
        }

        public List<String> getParamTypes() {
            return paramTypes;
        }

        public void setParamTypes(List<String> paramTypes) {
            this.paramTypes = paramTypes;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }

        public List<RequestParam> getRequest() {
            return request;
        }

        public void setRequest(List<RequestParam> request) {
            this.request = request;
        }

        public List<ResponseParam> getResponse() {
            return response;
        }

        public void setResponse(List<ResponseParam> response) {
            this.response = response;
        }
    }

    static class MethodHeader {
        private String description;
        private String example;
        private boolean hidden;
        private String key;
        private int position;
        private boolean required;
        private String value;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }

        public boolean isHidden() {
            return hidden;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    static class RequestParam {
        private boolean force;
        private boolean global;
        private String methodParamName;
        private String modelType;
        private String name;
        private List<ParamInfo> parameters;

        public boolean isForce() {
            return force;
        }

        public void setForce(boolean force) {
            this.force = force;
        }

        public boolean isGlobal() {
            return global;
        }

        public void setGlobal(boolean global) {
            this.global = global;
        }

        public String getMethodParamName() {
            return methodParamName;
        }

        public void setMethodParamName(String methodParamName) {
            this.methodParamName = methodParamName;
        }

        public String getModelType() {
            return modelType;
        }

        public void setModelType(String modelType) {
            this.modelType = modelType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ParamInfo> getParameters() {
            return parameters;
        }

        public void setParameters(List<ParamInfo> parameters) {
            this.parameters = parameters;
        }
    }

    static class ResponseParam {
        private String description;
        private boolean force;
        private boolean global;
        private List<String> group;
        private String modelType;
        private String name;
        private List<ParamInfo> parameters;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isForce() {
            return force;
        }

        public void setForce(boolean force) {
            this.force = force;
        }

        public boolean isGlobal() {
            return global;
        }

        public void setGlobal(boolean global) {
            this.global = global;
        }

        public List<String> getGroup() {
            return group;
        }

        public void setGroup(List<String> group) {
            this.group = group;
        }

        public String getModelType() {
            return modelType;
        }

        public void setModelType(String modelType) {
            this.modelType = modelType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ParamInfo> getParameters() {
            return parameters;
        }

        public void setParameters(List<ParamInfo> parameters) {
            this.parameters = parameters;
        }
    }

    static class ParamInfo {
        private String dataType;
        private int decimals;
        private String defaultValue;
        private String description;
        private String example;
        private List<String> extendDescr;
        private List<String> group;
        private boolean hidden;
        private String key;
        private String keyName;
        private int length;
        private String modelType;
        private String name;
        private String paramType;
        private int position;
        private boolean required;
        private String value;
        private List<String> version;
        private ChildrenParam children;

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public int getDecimals() {
            return decimals;
        }

        public void setDecimals(int decimals) {
            this.decimals = decimals;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }

        public List<String> getExtendDescr() {
            return extendDescr;
        }

        public void setExtendDescr(List<String> extendDescr) {
            this.extendDescr = extendDescr;
        }

        public List<String> getGroup() {
            return group;
        }

        public void setGroup(List<String> group) {
            this.group = group;
        }

        public boolean isHidden() {
            return hidden;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getKeyName() {
            return keyName;
        }

        public void setKeyName(String keyName) {
            this.keyName = keyName;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length = length;
        }

        public String getModelType() {
            return modelType;
        }

        public void setModelType(String modelType) {
            this.modelType = modelType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getParamType() {
            return paramType;
        }

        public void setParamType(String paramType) {
            this.paramType = paramType;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public List<String> getVersion() {
            return version;
        }

        public void setVersion(List<String> version) {
            this.version = version;
        }

        public ChildrenParam getChildren() {
            return children;
        }

        public void setChildren(ChildrenParam children) {
            this.children = children;
        }
    }

    static class ChildrenParam {
        private String description;
        private boolean force;
        private boolean global;
        private List<String> group;
        private String modelType;
        private String name;
        private List<ParamInfo> parameters;
        private String value;
        private List<String> version;

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isForce() {
            return force;
        }

        public void setForce(boolean force) {
            this.force = force;
        }

        public boolean isGlobal() {
            return global;
        }

        public void setGlobal(boolean global) {
            this.global = global;
        }

        public List<String> getGroup() {
            return group;
        }

        public void setGroup(List<String> group) {
            this.group = group;
        }

        public String getModelType() {
            return modelType;
        }

        public void setModelType(String modelType) {
            this.modelType = modelType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<ParamInfo> getParameters() {
            return parameters;
        }

        public void setParameters(List<ParamInfo> parameters) {
            this.parameters = parameters;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public List<String> getVersion() {
            return version;
        }

        public void setVersion(List<String> version) {
            this.version = version;
        }
    }

    // OpenAPI模型类
    static class OpenAPI {
        private Info info;
        private List<Server> servers;
        private Map<String, PathItem> paths;
        private Components components;
        private List<SecurityRequirement> security;
        private List<Tag> tags;

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

        public List<SecurityRequirement> getSecurity() {
            return security;
        }

        public void setSecurity(List<SecurityRequirement> security) {
            this.security = security;
        }

        public List<Tag> getTags() {
            return tags;
        }

        public void setTags(List<Tag> tags) {
            this.tags = tags;
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
        private List<String> tags;

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

        public List<String> getTags() {
            if (tags == null) tags = new ArrayList<>();
            return tags;
        }

        public Operation addTagsItem(String tag) {
            getTags().add(tag);
            return this;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
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

        public void setSchema(Schema schema) {
            this.schema = schema;
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

        public void setSchema(Schema schema) {
            this.schema = schema;
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

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
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

    static class Tag {
        private String name;
        private String description;

        public String getName() {
            return name;
        }

        public Tag name(String name) {
            this.name = name;
            return this;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public Tag description(String description) {
            this.description = description;
            return this;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
