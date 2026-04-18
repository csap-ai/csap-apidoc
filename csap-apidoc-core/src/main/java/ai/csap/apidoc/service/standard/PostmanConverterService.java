package ai.csap.apidoc.service.standard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.RequestMethod;

import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocMethodHeaders;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.type.ModelType;

import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Postman集合转换器服务
 * 将API文档转换为Postman规范的集合格式
 *
 * @author yangchengfu
 * @date 2024/01/01
 */
@Slf4j
public class PostmanConverterService {

    /**
     * 将API文档转换为Postman集合
     *
     * @param apiDocResponse API文档响应
     * @return Postman集合JSON字符串
     */
    public String convertToPostmanCollection(CsapDocResponse apiDocResponse) {
        try {
            PostmanCollection collection = buildPostmanCollection(apiDocResponse);
            Object collectionData = convertToJsonString(collection);
            // 如果需要返回JSON字符串，可以在这里进行转换
            return convertObjectToJsonString(collectionData);
        } catch (Exception e) {
            log.error("转换Postman集合失败", e);
            throw new RuntimeException("转换Postman集合失败", e);
        }
    }

    /**
     * 将API文档转换为Postman集合Map形式
     *
     * @param apiDocResponse API文档响应
     * @return Postman集合Map形式数据
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToPostmanCollectionMap(CsapDocResponse apiDocResponse) {
        try {
            PostmanCollection collection = buildPostmanCollection(apiDocResponse);
            Object result = convertToJsonString(collection);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            throw new RuntimeException("转换结果不是Map类型");
        } catch (Exception e) {
            log.error("转换Postman集合Map失败", e);
            throw new RuntimeException("转换Postman集合Map失败", e);
        }
    }

    /**
     * 构建Postman集合
     */
    private PostmanCollection buildPostmanCollection(CsapDocResponse apiDocResponse) {
        PostmanCollection collection = new PostmanCollection();

        // 设置基本信息
        collection.setInfo(buildCollectionInfo(apiDocResponse));

        // 构建项目列表
        List<PostmanItem> items = new ArrayList<>();

        // 处理全局API列表
        if (CollectionUtil.isNotEmpty(apiDocResponse.getGlobalApiList())) {
            for (CsapDocModelController controller : apiDocResponse.getGlobalApiList()) {
                items.addAll(buildControllerItems(controller, ""));
            }
        }
        // 处理全局API列表
        if (CollectionUtil.isNotEmpty(apiDocResponse.getApiList())) {
            for (CsapDocModelController controller : apiDocResponse.getApiList()) {
                items.addAll(buildControllerItems(controller, ""));
            }
        }
        collection.setItem(items);

        // 设置变量
        collection.setVariable(buildVariables(apiDocResponse));

        return collection;
    }

    /**
     * 构建集合信息
     */
    private PostmanCollectionInfo buildCollectionInfo(CsapDocResponse apiDocResponse) {
        PostmanCollectionInfo info = new PostmanCollectionInfo();
        info.setName("CSAP API Collection");
        info.setDescription("Generated from CSAP Framework API Documentation");
        info.setSchema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json");

        if (apiDocResponse.getApiInfo() != null) {
            info.setName(apiDocResponse.getApiInfo().getTitle());
            info.setDescription(apiDocResponse.getApiInfo().getDescription());
        }

        return info;
    }

    /**
     * 构建控制器项目
     */
    private List<PostmanItem> buildControllerItems(CsapDocModelController controller, String projectName) {
        List<PostmanItem> items = new ArrayList<>();
        if (controller.getMethodList() == null || controller.getMethodList().isEmpty()) {
            return items;
        }

        // 以 Controller 为分组，不再按标签分组
        String folderName = (controller.getValue() != null && !controller.getValue().isEmpty()) ?
                controller.getValue() :
                (controller.getSimpleName() != null && !controller.getSimpleName().isEmpty() ? controller.getSimpleName() : "default");

        PostmanItem controllerFolder = new PostmanItem();
        controllerFolder.setName(folderName);
        controllerFolder.setItem(new ArrayList<>());

        for (CsapDocMethod method : controller.getMethodList()) {
            if (Boolean.TRUE.equals(method.getHidden())) {
                continue;
            }
            PostmanItem requestItem = buildRequestItem(method, controller, projectName);
            if (requestItem != null) {
                controllerFolder.getItem().add(requestItem);
            }
        }

        if (!controllerFolder.getItem().isEmpty()) {
            items.add(controllerFolder);
        }
        return items;
    }

    /**
     * 构建请求项目
     */
    private PostmanItem buildRequestItem(CsapDocMethod method, CsapDocModelController controller, String projectName) {
        PostmanItem item = new PostmanItem();
        item.setName(method.getValue());

        PostmanRequest request = new PostmanRequest();
        if (method.getMethods() != null && !method.getMethods().isEmpty()) {
            request.setMethod(method.getMethods().get(0).name());
        } else {
            request.setMethod("GET");
        }

        // 构建URL（优先处理查询参数）
        PostmanUrl url;
        if (method.getParamNames() != null && !method.getParamNames().isEmpty() &&
                method.getParamType() != ParamType.BODY) {
            url = buildUrlWithQueryParams(method, controller);
        } else {
            url = new PostmanUrl(buildUrl(method, controller));
        }
        request.setUrl(url);

        // 构建请求头
        if (method.getMethodHeaders() != null && !method.getMethodHeaders().isEmpty()) {
            request.setHeader(buildHeaders(method.getMethodHeaders()));
        }

        // 构建请求体（只对POST/PUT/PATCH方法添加请求体）
        if (method.getRequest() != null && !method.getRequest().isEmpty() &&
                (method.getMethods().contains(RequestMethod.POST) ||
                        method.getMethods().contains(RequestMethod.PUT) ||
                        method.getMethods().contains(RequestMethod.PATCH))) {
            request.setBody(buildRequestBody(method.getRequest()));
        }

        item.setRequest(request);

        // 设置响应示例
        if (method.getResponse() != null && !method.getResponse().isEmpty()) {
            item.setResponse(buildResponses(method.getResponse(), method, controller));
        }

        return item;
    }

    /**
     * 构建URL
     */
    private String buildUrl(CsapDocMethod method, CsapDocModelController controller) {
        StringBuilder url = new StringBuilder();

        // 添加基础路径
        if (controller.getPath() != null && controller.getPath().length > 0) {
            url.append(controller.getPath()[0]);
        }

        // 添加方法路径
        if (method.getPaths() != null && method.getPaths().length > 0) {
            if (url.length() > 0 && !url.toString().endsWith("/")) {
                url.append("/");
            }
            url.append(method.getPaths()[0]);
        }

        return url.toString();
    }

    /**
     * 构建带查询参数的URL
     */
    private PostmanUrl buildUrlWithQueryParams(CsapDocMethod method, CsapDocModelController controller) {
        String baseUrl = buildUrl(method, controller);
        PostmanUrl url = new PostmanUrl(baseUrl);

        if (method.getRequest() != null && !method.getRequest().isEmpty() &&
                method.getParamType() != ParamType.BODY) {
            List<PostmanQueryParam> queryParams = new ArrayList<>();

            // 从请求模型中提取所有参数
            for (CsapDocModel requestModel : method.getRequest()) {
                if (requestModel.getParameters() != null) {
                    for (CsapDocParameter param : requestModel.getParameters()) {
                        // 只处理查询参数
                        if (param.getParamType() == ParamType.QUERY ||
                                param.getParamType() == ParamType.DEFAULT) {
                            PostmanQueryParam queryParam = new PostmanQueryParam();
                            queryParam.setKey(param.getName());
                            String ex = param.getExample();
                            String value = (ex != null && !ex.trim().isEmpty()) ? ex : getDefaultValueForParam(param.getName());
                            queryParam.setValue(value);
                            // disabled 按 required 取反：非必填则默认禁用
                            queryParam.setDisabled(!param.getRequired());
                            queryParam.setDescription(param.getDescription());
                            queryParams.add(queryParam);
                        }
                    }
                }
            }

            // 如果没有从模型中找到参数，使用paramNames
            if (queryParams.isEmpty() && method.getParamNames() != null && !method.getParamNames().isEmpty()) {
                for (String paramName : method.getParamNames()) {
                    PostmanQueryParam param = new PostmanQueryParam();
                    param.setKey(paramName);
                    param.setValue(getDefaultValueForParam(paramName));
                    param.setDescription(getDescriptionForParam(paramName, method));
                    param.setDisabled(Boolean.TRUE); // 无模型信息时默认禁用
                    queryParams.add(param);
                }
            }

            url.setQuery(queryParams);
        }

        return url;
    }

    /**
     * 获取参数的默认值
     */
    private String getDefaultValueForParam(String paramName) {
        switch (paramName) {
            case "id":
                return "1";
            case "currentPage":
                return "1";
            case "pageSize":
                return "20";
            case "ids":
                return "1,2,3";
            case "isSearchCount":
                return "true";
            case "leader":
                return "张三";
            case "name":
                return "技术部";
            case "parentId":
                return "0";
            case "phone":
                return "13800138000";
            case "status":
                return "1";
            case "seq":
                return "1";
            case "column":
                return "create_time";
            case "asc":
                return "true";
            case "updateTime":
                return "2024-01-01T00:00:00";
            case "createTime":
                return "2024-01-01T00:00:00";
            case "del":
                return "false";
            case "updateId":
                return "admin";
            case "createId":
                return "admin";
            default:
                return "";
        }
    }

    /**
     * 获取参数的描述
     */
    private String getDescriptionForParam(String paramName, CsapDocMethod method) {
        if (method.getRequest() != null) {
            for (CsapDocModel requestModel : method.getRequest()) {
                if (requestModel.getParameters() != null) {
                    for (CsapDocParameter param : requestModel.getParameters()) {
                        if (paramName.equals(param.getName())) {
                            return param.getDescription();
                        }
                    }
                }
            }
        }

        switch (paramName) {
            case "id":
                return "资源ID";
            case "currentPage":
                return "当前页数";
            case "pageSize":
                return "每页显示条数";
            case "ids":
                return "资源ID数组";
            default:
                return "";
        }
    }

    /**
     * 构建请求头
     */
    private List<PostmanHeader> buildHeaders(List<CsapDocMethodHeaders> methodHeaders) {
        List<PostmanHeader> headers = new ArrayList<>();

        for (CsapDocMethodHeaders header : methodHeaders) {
            PostmanHeader postmanHeader = new PostmanHeader();
            postmanHeader.setKey(header.getKey());
            postmanHeader.setValue(header.getValue());
            postmanHeader.setDescription(header.getDescription());
            headers.add(postmanHeader);
        }

        return headers;
    }

    /**
     * 构建请求体
     */
    private PostmanBody buildRequestBody(List<CsapDocModel> requestModels) {
        PostmanBody body = new PostmanBody();
        body.setMode("raw");

        // 设置options
        PostmanBodyOptions options = new PostmanBodyOptions();
        options.setRaw(new PostmanBodyRawOptions("json"));
        body.setOptions(options);

        if (requestModels != null && !requestModels.isEmpty()) {
            // 构建JSON请求体示例
            StringBuilder jsonBody = new StringBuilder();
            jsonBody.append("{\n");

            boolean first = true;
            for (CsapDocModel model : requestModels) {
                if (model.getParameters() != null) {
                    for (CsapDocParameter param : model.getParameters()) {
                        if (!first) {
                            jsonBody.append(",\n");
                        }
                        first = false;

                        jsonBody.append("  \"").append(param.getName()).append("\": ");
                        String defaultValue = getDefaultJsonValueForParam(param);
                        jsonBody.append(defaultValue);
                    }
                }
            }

            jsonBody.append("\n}");
            body.setRaw(jsonBody.toString());
        } else {
            body.setRaw("{}");
        }

        return body;
    }

    /**
     * 获取参数的默认JSON值
     */
    private String getDefaultJsonValueForParam(CsapDocParameter param) {
        String dataType = param.getDataType() == null ? "" : param.getDataType().toLowerCase();
        String example = param.getExample();
        boolean hasExample = example != null && !example.trim().isEmpty();
        if (dataType.contains("string")) {
            return "\"" + (hasExample ? escapeJsonString(example) : "示例值") + "\"";
        } else if (dataType.contains("long") || dataType.contains("int")) {
            return hasExample ? example : "0";
        } else if (dataType.contains("boolean")) {
            return hasExample ? example : "true";
        } else if (dataType.contains("date") || dataType.contains("time")) {
            return "\"2024-01-01T00:00:00\"";
        } else {
            return "\"" + (hasExample ? escapeJsonString(example) : "") + "\"";
        }
    }

    /**
     * 构建响应示例
     */
    private List<PostmanResponse> buildResponses(List<CsapDocModel> responseModels, CsapDocMethod method, CsapDocModelController controller) {
        List<PostmanResponse> responses = new ArrayList<>();

        if (responseModels != null && !responseModels.isEmpty()) {
            // 1. 成功响应
            responses.add(buildSuccessResponse(responseModels, method, controller));

            // 2. 错误响应示例
            responses.add(buildBadRequestResponse(method, controller));
            responses.add(buildNotFoundResponse(method, controller));
            responses.add(buildRateLimitResponse(method, controller));
        }

        return responses;
    }

    /**
     * 构建成功响应
     */
    private PostmanResponse buildSuccessResponse(List<CsapDocModel> responseModels, CsapDocMethod method, CsapDocModelController controller) {
        PostmanResponse response = new PostmanResponse();
        response.setName("Successful Response");

        // 构建完整的originalRequest
        PostmanOriginalRequest originalRequest = buildOriginalRequest(method, controller);
        response.setOriginalRequest(originalRequest);

        response.setStatus("OK");
        response.setCode(200);
        response.set_postman_previewlanguage("json");
        response.setCookie(new ArrayList<>());

        // 构建响应头
        List<PostmanHeader> responseHeaders = new ArrayList<>();
        PostmanHeader contentTypeHeader = new PostmanHeader();
        contentTypeHeader.setKey("Content-Type");
        contentTypeHeader.setValue("application/json");
        contentTypeHeader.setDescription("");
        responseHeaders.add(contentTypeHeader);
        response.setHeader(responseHeaders);

        // 构建响应体示例
        StringBuilder responseBody = new StringBuilder();
        responseBody.append("{\n");
        responseBody.append("    \"code\": \"0\",\n");
        responseBody.append("    \"message\": \"成功\",\n");
        responseBody.append("    \"data\": ").append(buildResponseDataExample(responseModels)).append(",\n");
        responseBody.append("    \"language\": \"\",\n");
        responseBody.append("    \"time\": 1540483321460\n");
        responseBody.append("}");

        response.setBody(responseBody.toString());
        return response;
    }

    /**
     * 构建400错误响应
     */
    private PostmanResponse buildBadRequestResponse(CsapDocMethod method, CsapDocModelController controller) {
        PostmanResponse response = new PostmanResponse();
        response.setName("Bad Request");
        response.setOriginalRequest(buildOriginalRequest(method, controller));
        response.setStatus("Bad Request");
        response.setCode(400);
        response.set_postman_previewlanguage("json");
        response.setCookie(new ArrayList<>());

        List<PostmanHeader> responseHeaders = new ArrayList<>();
        PostmanHeader contentTypeHeader = new PostmanHeader();
        contentTypeHeader.setKey("Content-Type");
        contentTypeHeader.setValue("application/json");
        contentTypeHeader.setDescription("");
        responseHeaders.add(contentTypeHeader);
        response.setHeader(responseHeaders);

        response.setBody("{\n    \"code\": \"400\",\n    \"message\": \"请求参数错误\",\n    \"data\": null\n}");
        return response;
    }

    /**
     * 构建404错误响应
     */
    private PostmanResponse buildNotFoundResponse(CsapDocMethod method, CsapDocModelController controller) {
        PostmanResponse response = new PostmanResponse();
        response.setName("Not Found");
        response.setOriginalRequest(buildOriginalRequest(method, controller));
        response.setStatus("Not Found");
        response.setCode(404);
        response.set_postman_previewlanguage("json");
        response.setCookie(new ArrayList<>());

        List<PostmanHeader> responseHeaders = new ArrayList<>();
        PostmanHeader contentTypeHeader = new PostmanHeader();
        contentTypeHeader.setKey("Content-Type");
        contentTypeHeader.setValue("application/json");
        contentTypeHeader.setDescription("");
        responseHeaders.add(contentTypeHeader);
        response.setHeader(responseHeaders);

        response.setBody("{\n    \"code\": \"404\",\n    \"message\": \"资源不存在\",\n    \"data\": null\n}");
        return response;
    }

    /**
     * 构建429限流响应
     */
    private PostmanResponse buildRateLimitResponse(CsapDocMethod method, CsapDocModelController controller) {
        PostmanResponse response = new PostmanResponse();
        response.setName("Rate Limit Exceeded");
        response.setOriginalRequest(buildOriginalRequest(method, controller));
        response.setStatus("Too Many Requests");
        response.setCode(429);
        response.set_postman_previewlanguage("json");
        response.setCookie(new ArrayList<>());

        List<PostmanHeader> responseHeaders = new ArrayList<>();
        PostmanHeader contentTypeHeader = new PostmanHeader();
        contentTypeHeader.setKey("Content-Type");
        contentTypeHeader.setValue("application/json");
        contentTypeHeader.setDescription("");
        responseHeaders.add(contentTypeHeader);
        response.setHeader(responseHeaders);

        response.setBody("{\n    \"error\": \"rateLimited\",\n    \"message\": \"Rate limit exceeded. Please retry later\"\n}");
        return response;
    }

    /**
     * 构建originalRequest
     */
    private PostmanOriginalRequest buildOriginalRequest(CsapDocMethod method, CsapDocModelController controller) {
        PostmanOriginalRequest originalRequest = new PostmanOriginalRequest();
        originalRequest.setMethod(method.getMethods() != null && !method.getMethods().isEmpty() ?
                method.getMethods().get(0).name() : "GET");
        originalRequest.setHeader(method.getMethodHeaders() != null ?
                buildHeaders(method.getMethodHeaders()) : new ArrayList<>());

        // 构建originalRequest的URL
        PostmanUrl originalUrl;
        if (method.getRequest() != null && !method.getRequest().isEmpty() &&
                method.getParamType() != ParamType.BODY) {
            originalUrl = buildUrlWithQueryParams(method, controller);
        } else {
            originalUrl = new PostmanUrl(buildUrl(method, controller));
        }
        originalRequest.setUrl(originalUrl);

        // 如果是POST/PUT请求，添加请求体
        if (method.getRequest() != null && !method.getRequest().isEmpty() &&
                (method.getMethods().contains(RequestMethod.POST) ||
                        method.getMethods().contains(RequestMethod.PUT) ||
                        method.getMethods().contains(RequestMethod.PATCH))) {
            originalRequest.setBody(buildRequestBody(method.getRequest()));
        }

        return originalRequest;
    }

    /**
     * 构建响应数据示例
     */
    private String buildResponseDataExample(List<CsapDocModel> responseModels) {
        if (responseModels == null || responseModels.isEmpty()) {
            return "{}";
        }
        CsapDocModel wrapperModel = responseModels.get(0);
        if (wrapperModel.getParameters() == null || wrapperModel.getParameters().isEmpty()) {
            return "{}";
        }
        // 在包装模型中查找名为 data 的字段，并基于其子结构生成完整示例
        for (CsapDocParameter param : wrapperModel.getParameters()) {
            if ("data".equals(param.getName())) {
                return buildParamValueJson(param);
            }
        }
        return "{}";
    }

    private String buildParamValueJson(CsapDocParameter param) {
        // 如果有子参数，按对象处理
        List<CsapDocParameter> childParams = param.getParameters();
        if (childParams != null && !childParams.isEmpty()) {
            // 若自身为数组，则返回数组包裹的对象
            if (isArrayParam(param)) {
                return "[" + buildJsonObjectFromParams(childParams) + "]";
            }
            return buildJsonObjectFromParams(childParams);
        }
        // 数组类型处理
        if (isArrayParam(param)) {
            // 基础类型数组
            return "[" + getDefaultJsonValueForParam(param) + "]";
        }
        // 基础类型
        return getDefaultJsonValueForParam(param);
    }

    private boolean isArrayParam(CsapDocParameter param) {
        if (param.getModelType() == ModelType.ARRAY) {
            return true;
        }
        String dt = param.getDataType() == null ? "" : param.getDataType().toLowerCase();
        return dt.contains("[]") || dt.contains("list<") || dt.contains("set<");
    }

    private String buildJsonObjectFromParams(List<CsapDocParameter> params) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        boolean firstField = true;
        for (CsapDocParameter p : params) {
            if (Boolean.TRUE.equals(p.getHidden())) {
                continue;
            }
            if (!firstField) {
                json.append(",\n");
            }
            firstField = false;
            json.append("    \"").append(p.getName()).append("\": ");
            // 递归生成值
            List<CsapDocParameter> grandChildren = p.getParameters();
            if (isArrayParam(p)) {
                if (grandChildren != null && !grandChildren.isEmpty()) {
                    json.append("[").append(buildJsonObjectFromParams(grandChildren)).append("]");
                } else {
                    json.append("[").append(getDefaultJsonValueForParam(p)).append("]");
                }
            } else if (grandChildren != null && !grandChildren.isEmpty()) {
                json.append(buildJsonObjectFromParams(grandChildren));
            } else {
                json.append(buildParamValueJson(p));
            }
        }
        json.append("\n  }");
        return json.toString();
    }

    /**
     * 构建变量
     */
    private List<PostmanVariable> buildVariables(CsapDocResponse apiDocResponse) {
        List<PostmanVariable> variables = new ArrayList<>();

        // 添加基础URL变量
        PostmanVariable baseUrl = new PostmanVariable();
        baseUrl.setKey("baseUrl");
        baseUrl.setValue("http://localhost:8080");
        baseUrl.setDescription("API基础URL");
        variables.add(baseUrl);

        return variables;
    }

    /**
     * JSON字符串转义方法
     */
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

    /**
     * 将Map转换为JSON字符串的辅助方法
     */
    private String convertMapToJsonString(Map<String, Object> map) {
        if (map == null) {
            return "{}";
        }
        StringBuilder json = new StringBuilder();
        json.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(escapeJsonString(entry.getKey())).append("\":");
            json.append(convertObjectToJsonString(entry.getValue()));
        }
        json.append("}");
        return json.toString();
    }

    /**
     * 将Object转换为JSON字符串的辅助方法
     */
    private String convertObjectToJsonString(Object obj) {
        if (obj == null) {
            return "null";
        } else if (obj instanceof String) {
            return "\"" + escapeJsonString((String) obj) + "\"";
        } else if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            StringBuilder json = new StringBuilder();
            json.append("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    json.append(",");
                }
                json.append(convertObjectToJsonString(list.get(i)));
            }
            json.append("]");
            return json.toString();
        } else if (obj instanceof Map) {
            return convertMapToJsonString((Map<String, Object>) obj);
        }
        return "\"" + escapeJsonString(obj.toString()) + "\"";
    }

    /**
     * 简单的JSON转换方法
     */
    private Object convertToJsonString(Object obj) {
        // 这里使用Map构建来避免Jackson依赖问题
        // 在实际项目中，应该使用Jackson或其他JSON库
        if (obj instanceof PostmanCollection) {
            PostmanCollection collection = (PostmanCollection) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("info", convertToJsonString(collection.getInfo()));
            map.put("item", convertToJsonString(collection.getItem()));
            map.put("variable", convertToJsonString(collection.getVariable()));
            return map;
        } else if (obj instanceof PostmanCollectionInfo) {
            PostmanCollectionInfo info = (PostmanCollectionInfo) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", info.getName());
            map.put("description", info.getDescription());
            map.put("schema", info.getSchema());
            return map;
        } else if (obj instanceof PostmanItem) {
            PostmanItem item = (PostmanItem) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", item.getName());

            if (item.getRequest() != null) {
                map.put("request", convertToJsonString(item.getRequest()));
            }

            if (item.getResponse() != null && !item.getResponse().isEmpty()) {
                map.put("response", convertToJsonString(item.getResponse()));
            }

            if (item.getItem() != null && !item.getItem().isEmpty()) {
                map.put("item", convertToJsonString(item.getItem()));
            }

            return map;
        } else if (obj instanceof PostmanRequest) {
            PostmanRequest request = (PostmanRequest) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("method", request.getMethod());

            if (request.getUrl() != null) {
                map.put("url", convertToJsonString(request.getUrl()));
            }

            if (request.getHeader() != null && !request.getHeader().isEmpty()) {
                map.put("header", convertToJsonString(request.getHeader()));
            }

            if (request.getBody() != null) {
                map.put("body", convertToJsonString(request.getBody()));
            }

            return map;
        } else if (obj instanceof PostmanUrl) {
            PostmanUrl url = (PostmanUrl) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("raw", url.getRaw());

            if (url.getProtocol() != null) {
                map.put("protocol", url.getProtocol());
            }

            if (url.getHost() != null && !url.getHost().isEmpty()) {
                map.put("host", url.getHost());
            }

            if (url.getPath() != null && !url.getPath().isEmpty()) {
                map.put("path", url.getPath());
            }

            if (url.getQuery() != null && !url.getQuery().isEmpty()) {
                map.put("query", convertToJsonString(url.getQuery()));
            }

            return map;
        } else if (obj instanceof PostmanHeader) {
            PostmanHeader header = (PostmanHeader) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            String desc = header.getDescription() == null ? "" : header.getDescription();
            String key = header.getKey() == null ? "" : header.getKey();
            String value = header.getValue() == null ? "" : header.getValue();

            map.put("key", key);
            map.put("value", value);
            map.put("name", key);

            Map<String, Object> descMap = new LinkedHashMap<>();
            descMap.put("content", desc);
            descMap.put("type", "text/plain");
            map.put("description", descMap);

            return map;
        } else if (obj instanceof PostmanBody) {
            PostmanBody body = (PostmanBody) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("mode", body.getMode());
            map.put("raw", body.getRaw());

            if (body.getOptions() != null) {
                map.put("options", convertToJsonString(body.getOptions()));
            }

            return map;
        } else if (obj instanceof PostmanBodyOptions) {
            PostmanBodyOptions options = (PostmanBodyOptions) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("raw", convertToJsonString(options.getRaw()));
            return map;
        } else if (obj instanceof PostmanBodyRawOptions) {
            PostmanBodyRawOptions rawOptions = (PostmanBodyRawOptions) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("language", rawOptions.getLanguage());
            return map;
        } else if (obj instanceof PostmanResponse) {
            PostmanResponse response = (PostmanResponse) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", response.getName());

            if (response.getOriginalRequest() != null) {
                map.put("originalRequest", convertToJsonString(response.getOriginalRequest()));
            }

            map.put("status", response.getStatus());
            map.put("code", response.getCode());

            if (response.get_postman_previewlanguage() != null) {
                map.put("_postman_previewlanguage", response.get_postman_previewlanguage());
            }

            map.put("header", convertToJsonString(response.getHeader()));
            map.put("cookie", convertToJsonString(response.getCookie()));
            map.put("body", response.getBody());
            return map;
        } else if (obj instanceof PostmanOriginalRequest) {
            PostmanOriginalRequest originalRequest = (PostmanOriginalRequest) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("method", originalRequest.getMethod());

            if (originalRequest.getHeader() != null && !originalRequest.getHeader().isEmpty()) {
                map.put("header", convertToJsonString(originalRequest.getHeader()));
            }

            if (originalRequest.getBody() != null) {
                map.put("body", convertToJsonString(originalRequest.getBody()));
            }

            if (originalRequest.getUrl() != null) {
                map.put("url", convertToJsonString(originalRequest.getUrl()));
            }

            return map;
        } else if (obj instanceof PostmanVariable) {
            PostmanVariable variable = (PostmanVariable) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", variable.getKey());
            map.put("value", variable.getValue());
            map.put("description", variable.getDescription());
            return map;
        } else if (obj instanceof PostmanQueryParam) {
            PostmanQueryParam param = (PostmanQueryParam) obj;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", param.getKey());
            map.put("value", param.getValue());
            if (param.getDescription() != null && !param.getDescription().isEmpty()) {
                map.put("description", param.getDescription());
            }
            if (param.getDisabled() != null && param.getDisabled()) {
                map.put("disabled", true);
            }
            return map;
        } else if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            if (list.isEmpty()) {
                return new ArrayList<>();
            }
            List<Object> resultList = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    resultList.add(item);
                } else {
                    resultList.add(convertToJsonString(item));
                }
            }
            return resultList;
        } else if (obj instanceof String) {
            return obj;
        }
        return new LinkedHashMap<>();
    }

    // Postman集合模型类
    public static class PostmanCollection {
        /**
         * 集合的基本信息（名称、描述、schema）
         */
        private PostmanCollectionInfo info;
        /**
         * 顶层的集合条目，既可以是请求，也可以是分组（文件夹）
         */
        private List<PostmanItem> item;
        /**
         * 集合级变量（如 baseUrl 等环境变量占位）
         */
        private List<PostmanVariable> variable;

        // Getters and Setters
        public PostmanCollectionInfo getInfo() {
            return info;
        }

        public void setInfo(PostmanCollectionInfo info) {
            this.info = info;
        }

        public List<PostmanItem> getItem() {
            return item;
        }

        public void setItem(List<PostmanItem> item) {
            this.item = item;
        }

        public List<PostmanVariable> getVariable() {
            return variable;
        }

        public void setVariable(List<PostmanVariable> variable) {
            this.variable = variable;
        }
    }

    public static class PostmanCollectionInfo {
        /**
         * 集合名称（显示在 Postman 左侧栏）
         */
        private String name;
        /**
         * 集合描述（对集合用途和背景的说明）
         */
        private String description;
        /**
         * Postman 集合规范 schema 地址（固定为 v2.1.0）
         */
        private String schema;

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

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }
    }

    public static class PostmanItem {
        /**
         * 条目名称（请求名称或文件夹名称）
         */
        private String name;
        /**
         * 请求详情（当该条目为请求时有效）
         */
        private PostmanRequest request;
        /**
         * 示例响应列表（包含 originalRequest、status、code、body 等）
         */
        private List<PostmanResponse> response;
        /**
         * 子条目（当该条目为文件夹时用于包含子请求或子文件夹）
         */
        private List<PostmanItem> item;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public PostmanRequest getRequest() {
            return request;
        }

        public void setRequest(PostmanRequest request) {
            this.request = request;
        }

        public List<PostmanResponse> getResponse() {
            return response;
        }

        public void setResponse(List<PostmanResponse> response) {
            this.response = response;
        }

        public List<PostmanItem> getItem() {
            return item;
        }

        public void setItem(List<PostmanItem> item) {
            this.item = item;
        }
    }

    public static class PostmanRequest {
        /**
         * HTTP 方法，例如 GET/POST/PUT/DELETE
         */
        private String method;
        /**
         * 请求头列表（如 Content-Type、Authorization 等）
         */
        private List<PostmanHeader> header;
        /**
         * 请求体（raw/json 等模式及内容）
         */
        private PostmanBody body;
        /**
         * 请求 URL（包含 raw、path、query 等）
         */
        private PostmanUrl url;

        // Getters and Setters
        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public List<PostmanHeader> getHeader() {
            return header;
        }

        public void setHeader(List<PostmanHeader> header) {
            this.header = header;
        }

        public PostmanBody getBody() {
            return body;
        }

        public void setBody(PostmanBody body) {
            this.body = body;
        }

        public PostmanUrl getUrl() {
            return url;
        }

        public void setUrl(PostmanUrl url) {
            this.url = url;
        }
    }

    public static class PostmanUrl {
        /**
         * 原始 URL 字符串（支持 {{变量}} 占位）
         */
        private String raw;
        /**
         * 协议（http/https），当 raw 为完整 URL 时解析得到
         */
        private String protocol;
        /**
         * 主机名分片（例如 api.getpostman.com 拆分为 ["api","getpostman","com"]）
         */
        private List<String> host;
        /**
         * 路径分片（例如 /dept/findById 拆分为 ["dept","findById"]）
         */
        private List<String> path;
        /**
         * 查询参数列表（key/value/description）
         */
        private List<PostmanQueryParam> query;

        public PostmanUrl(String raw) {
            this.raw = raw;
            parseUrl(raw);
        }

        private void parseUrl(String url) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                // 完整URL解析
                if (url.startsWith("https://")) {
                    this.protocol = "https";
                    url = url.substring(8);
                } else {
                    this.protocol = "http";
                    url = url.substring(7);
                }

                String[] parts = url.split("/", 2);
                if (parts.length > 0) {
                    this.host = Arrays.asList(parts[0].split("\\."));
                    if (parts.length > 1) {
                        this.path = Arrays.asList(parts[1].split("/"));
                    }
                }
            } else {
                // 相对路径，使用变量
                this.raw = "{{baseUrl}}/" + url;
                this.path = Arrays.asList(url.split("/"));
            }
        }

        // Getters and Setters
        public String getRaw() {
            return raw;
        }

        public void setRaw(String raw) {
            this.raw = raw;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public List<String> getHost() {
            return host;
        }

        public void setHost(List<String> host) {
            this.host = host;
        }

        public List<String> getPath() {
            return path;
        }

        public void setPath(List<String> path) {
            this.path = path;
        }

        public List<PostmanQueryParam> getQuery() {
            return query;
        }

        public void setQuery(List<PostmanQueryParam> query) {
            this.query = query;
        }
    }

    public static class PostmanQueryParam {
        /**
         * 参数名（query 键）
         */
        private String key;
        /**
         * 参数值（默认值或示例值）
         */
        private String value;
        /**
         * 参数说明（业务含义或使用说明）
         */
        private String description;
        /**
         * 是否禁用（当非必填时可设为 true 以便默认不发送）
         */
        private Boolean disabled;

        // Getters and Setters
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Boolean getDisabled() {
            return disabled;
        }

        public void setDisabled(Boolean disabled) {
            this.disabled = disabled;
        }
    }

    public static class PostmanHeader {
        /**
         * 头名称（如 Content-Type）
         */
        private String key;
        /**
         * 头的值（如 application/json）
         */
        private String value;
        /**
         * 头说明（用途或要求）
         */
        private String description;

        // Getters and Setters
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class PostmanBody {
        /**
         * 请求体模式（如 raw、formdata、urlencoded 等）
         */
        private String mode;
        /**
         * 原始请求体内容（当 mode=raw 时存放 JSON 文本等）
         */
        private String raw;
        /**
         * 额外选项（如 raw.language 指定编辑器高亮语言）
         */
        private PostmanBodyOptions options;

        // Getters and Setters
        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getRaw() {
            return raw;
        }

        public void setRaw(String raw) {
            this.raw = raw;
        }

        public PostmanBodyOptions getOptions() {
            return options;
        }

        public void setOptions(PostmanBodyOptions options) {
            this.options = options;
        }
    }

    public static class PostmanBodyOptions {
        /**
         * raw 模式下的附加选项（如语言）
         */
        private PostmanBodyRawOptions raw;

        // Getters and Setters
        public PostmanBodyRawOptions getRaw() {
            return raw;
        }

        public void setRaw(PostmanBodyRawOptions raw) {
            this.raw = raw;
        }
    }

    public static class PostmanBodyRawOptions {
        /**
         * 原始请求体语言（如 json、text、xml）
         */
        private String language;

        public PostmanBodyRawOptions(String language) {
            this.language = language;
        }

        // Getters and Setters
        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    public static class PostmanResponse {
        /**
         * 响应示例名称（如 Successful Response/Bad Request 等）
         */
        private String name;
        /**
         * 该响应对应的原始请求（方法、头、URL、Body 等）
         */
        private PostmanOriginalRequest originalRequest;
        /**
         * HTTP 状态文本（如 OK/Bad Request/Not Found）
         */
        private String status;
        /**
         * HTTP 状态码（如 200/400/404/429）
         */
        private int code;
        /**
         * Postman 预览语言（如 json）
         */
        private String _postman_previewlanguage;
        /**
         * 响应头列表（如 Content-Type）
         */
        private List<PostmanHeader> header;
        /**
         * 响应 Cookie 列表（通常为空数组）
         */
        private List<Object> cookie;
        /**
         * 响应体示例内容（字符串形式保存 JSON 文本）
         */
        private String body;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public PostmanOriginalRequest getOriginalRequest() {
            return originalRequest;
        }

        public void setOriginalRequest(PostmanOriginalRequest originalRequest) {
            this.originalRequest = originalRequest;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String get_postman_previewlanguage() {
            return _postman_previewlanguage;
        }

        public void set_postman_previewlanguage(String _postman_previewlanguage) {
            this._postman_previewlanguage = _postman_previewlanguage;
        }

        public List<PostmanHeader> getHeader() {
            return header;
        }

        public void setHeader(List<PostmanHeader> header) {
            this.header = header;
        }

        public List<Object> getCookie() {
            return cookie;
        }

        public void setCookie(List<Object> cookie) {
            this.cookie = cookie;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }

    public static class PostmanOriginalRequest {
        /**
         * 原始请求的 HTTP 方法
         */
        private String method;
        /**
         * 原始请求头
         */
        private List<PostmanHeader> header;
        /**
         * 原始请求体
         */
        private PostmanBody body;
        /**
         * 原始请求 URL
         */
        private PostmanUrl url;

        // Getters and Setters
        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public List<PostmanHeader> getHeader() {
            return header;
        }

        public void setHeader(List<PostmanHeader> header) {
            this.header = header;
        }

        public PostmanBody getBody() {
            return body;
        }

        public void setBody(PostmanBody body) {
            this.body = body;
        }

        public PostmanUrl getUrl() {
            return url;
        }

        public void setUrl(PostmanUrl url) {
            this.url = url;
        }
    }

    public static class PostmanVariable {
        /**
         * 变量键（如 baseUrl）
         */
        private String key;
        /**
         * 变量值（默认值）
         */
        private String value;
        /**
         * 变量说明（用途或默认指向）
         */
        private String description;

        // Getters and Setters
        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
