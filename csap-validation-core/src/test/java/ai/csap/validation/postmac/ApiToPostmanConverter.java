package ai.csap.validation.postmac;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ApiToPostmanConverter {
    public static void main(String[] args) {
        try {
            // 读取源API文档（api.json）
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode apiJson = objectMapper.readTree(new File("/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/csap-framework-validation-core/src/test/resources/api.json"));

            // 初始化Postman Collection
            PostmanCollection postmanCollection = new PostmanCollection();
            Info info = new Info();
            info.set_postman_id(UUID.randomUUID().toString());
            // 从apiInfo中提取标题和版本
            JsonNode apiInfo = apiJson.get("apiInfo");
            info.setName(apiInfo.get("title").asText("API Collection"));
            postmanCollection.setInfo(info);

            // 配置认证（根据apiInfo中的authorizationType）
            if (apiInfo.has("authorizationType") && "OAUTH_2".equals(apiInfo.get("authorizationType").asText())) {
                Auth auth = new Auth();
                auth.setType("oauth2");
                // 构建OAuth2认证参数（简化示例）
                Map<String, String> oauthParam = new HashMap<>();
                oauthParam.put("token", "{{access_token}}");
                auth.getApikey().add(oauthParam); // 实际应根据OAuth2规范调整结构
                postmanCollection.setAuth(auth);
            }

            // 添加基础URL变量（从apiInfo的serviceUrl提取）
            Variable baseUrlVar = new Variable();
            baseUrlVar.setKey("baseUrl");
            baseUrlVar.setValue(apiInfo.get("serviceUrl").asText("https://api.example.com"));
            postmanCollection.getVariable().add(baseUrlVar);

            // 解析apiList中的接口信息
            JsonNode apiList = apiJson.get("apiList");
            if (apiList != null && apiList.isArray()) {
                for (JsonNode api : apiList) {
                    // 解析methodList中的每个方法
                    JsonNode methodList = api.get("methodList");
                    if (methodList != null && methodList.isArray()) {
                        for (JsonNode method : methodList) {
                            Item item = new Item();
                            // 接口名称（优先取value，无则用name）
                            item.setName(method.get("value").asText(method.get("name").asText("Unnamed Request")));

                            // 构建请求
                            Request request = new Request();
                            // 请求方法（methods数组的第一个元素）
                            JsonNode methods = method.get("methods");
                            if (methods != null && methods.isArray() && methods.size() > 0) {
                                request.setMethod(methods.get(0).asText("GET"));
                            }

                            // 构建URL
                            Url url = new Url();
                            url.setProtocol("https");
                            // 解析host（从baseUrl提取）
                            String baseUrl = baseUrlVar.getValue();
                            if (baseUrl.startsWith("http")) {
                                baseUrl = baseUrl.replace("https://", "").replace("http://", "");
                            }
                            for (String hostPart : baseUrl.split("\\.")) {
                                if (!hostPart.isEmpty()) {
                                    url.getHost().add(hostPart);
                                }
                            }
                            // 解析路径（apiPath + paths）
                            JsonNode apiPath = method.get("apiPath");
                            if (apiPath != null && apiPath.isArray()) {
                                for (JsonNode pathPart : apiPath) {
                                    url.getPath().add(pathPart.asText());
                                }
                            }
                            JsonNode paths = method.get("paths");
                            if (paths != null && paths.isArray()) {
                                for (JsonNode pathPart : paths) {
                                    url.getPath().add(pathPart.asText());
                                }
                            }
                            // 拼接原始URL
                            url.setRaw(baseUrlVar.getValue() + "/" + String.join("/", url.getPath()));

                            // 添加查询参数（从request中的parameters提取）
                            JsonNode requests = method.get("request");
                            if (requests != null && requests.isArray()) {
                                for (JsonNode req : requests) {
                                    JsonNode params = req.get("parameters");
                                    if (params != null && params.isArray()) {
                                        for (JsonNode param : params) {
                                            QueryParam queryParam = new QueryParam();
                                            queryParam.setKey(param.get("name").asText());
                                            queryParam.setValue(param.get("defaultValue").asText());
                                            queryParam.setDescription(param.get("description").asText(""));
                                            queryParam.setDisabled(!param.get("required").asBoolean(false));
                                            url.getQuery().add(queryParam);
                                        }
                                    }
                                }
                            }
                            request.setUrl(url);

                            // 添加请求头（从methodHeaders提取）
                            JsonNode methodHeaders = method.get("methodHeaders");
                            if (methodHeaders != null && methodHeaders.isArray()) {
                                for (JsonNode header : methodHeaders) {
                                    Header reqHeader = new Header();
                                    reqHeader.setKey(header.get("key").asText());
                                    reqHeader.setValue(header.get("value").asText());
                                    reqHeader.setDescription(header.get("description").asText(""));
                                    request.getHeader().add(reqHeader);
                                }
                            }

                            // 添加请求体（如果是POST/PUT且有请求参数）
                            String methodType = request.getMethod();
                            if (("POST".equals(methodType) || "PUT".equals(methodType)) && requests != null) {
                                Body body = new Body();
                                body.setMode("raw");
                                body.setRaw(buildRequestBody(requests)); // 构建JSON请求体
                                request.setBody(body);
                            }

                            // 添加接口描述
                            request.setDescription(method.get("description").asText(""));
                            item.setRequest(request);

                            // 添加响应示例（从response提取）
                            JsonNode responses = method.get("response");
                            if (responses != null && responses.isArray()) {
                                for (JsonNode res : responses) {
                                    Response response = new Response();
                                    response.setName("Response: " + res.get("description").asText("Success"));
                                    response.setStatus("OK");
                                    response.setCode(200);
                                    response.set_postman_previewlanguage("json");
                                    // 响应头（默认JSON类型）
                                    Header resHeader = new Header();
                                    resHeader.setKey("Content-Type");
                                    resHeader.setValue("application/json");
                                    response.getHeader().add(resHeader);
                                    // 响应体（构建示例JSON）
                                    response.setBody(buildResponseBody(res));
                                    item.getResponse().add(response);
                                }
                            }

                            // 添加到集合
                            postmanCollection.getItem().add(item);
                        }
                    }
                }
            }

            // 生成Postman Collection文件
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File("/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/csap-framework-validation-core/src/test/resources/converted_postman_collection.json"), postmanCollection);
            System.out.println("转换完成，生成文件：converted_postman_collection.json");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 构建请求体JSON
    private static String buildRequestBody(JsonNode requests) {
        Map<String, Object> bodyMap = new HashMap<>();
        for (JsonNode req : requests) {
            JsonNode params = req.get("parameters");
            if (params != null && params.isArray()) {
                for (JsonNode param : params) {
                    bodyMap.put(param.get("name").asText(), param.get("example").asText(""));
                }
            }
        }
        try {
            return new ObjectMapper().writeValueAsString(bodyMap);
        } catch (Exception e) {
            return "{}";
        }
    }

    // 构建响应体JSON
    private static String buildResponseBody(JsonNode response) {
        Map<String, Object> resMap = new HashMap<>();
        JsonNode params = response.get("parameters");
        if (params != null && params.isArray()) {
            for (JsonNode param : params) {
                resMap.put(param.get("name").asText(), param.get("example").asText(""));
            }
        }
        try {
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(resMap);
        } catch (Exception e) {
            return "{}";
        }
    }
}
