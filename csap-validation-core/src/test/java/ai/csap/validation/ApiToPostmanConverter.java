package ai.csap.validation;

import static ai.csap.validation.ApiToOpenApiConverterModels.*;
import static ai.csap.validation.ApiToPostmanConverterModels.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 将自定义API格式转换为Postman集合格式的转换器
 */
public class ApiToPostmanConverter {
    public static final String PATH = "/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/csap-framework-validation-core/src/test/resources/";

    public static void main(String[] args) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 读取api.json文件
            ApiListData apiListData = objectMapper.readValue(
                    new File(PATH + "api.json"), ApiListData.class);

            // 构建Postman集合对象
            PostmanCollection postmanCollection = buildPostmanCollection(apiListData);

            // 将Postman集合对象写入文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    new File(PATH + "postman_collection.json"), postmanCollection);

            System.out.println("转换完成，已生成postman_collection.json文件");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建Postman集合对象
     */
    public static PostmanCollection buildPostmanCollection(ApiListData apiListData) {
        PostmanCollection collection = new PostmanCollection();

        // 设置集合基本信息
        ApiInfo apiInfo = apiListData.getApiInfo();
        ApiToPostmanConverterModels.Info info = new ApiToPostmanConverterModels.Info();
        info.setName(apiInfo != null ? apiInfo.getTitle() : "API Collection");
        info.setDescription(apiInfo != null ? apiInfo.getDescription() : "Generated API Collection");
        info.setSchema("https://schema.getpostman.com/json/collection/v2.1.0/collection.json");
        info.set_postman_id(UUID.randomUUID().toString());
        collection.setInfo(info);

        // 设置环境变量
        List<Variable> variables = new ArrayList<>();
        Variable baseUrlVar = new Variable();
        baseUrlVar.setKey("baseUrl");
        baseUrlVar.setValue(apiInfo != null ? apiInfo.getServiceUrl() : "http://localhost");
        baseUrlVar.setType("string");
        variables.add(baseUrlVar);
        collection.setVariable(variables);

        // 处理API分组和路径
        Map<String, Item> folderMap = new HashMap<>();

        if (apiListData.getApiList() != null) {
            for (ApiGroup apiGroup : apiListData.getApiList()) {
                if (apiGroup == null || apiGroup.getMethodList() == null) {
                    continue;
                }

                // 为每个API组创建一个文件夹
                String groupName = apiGroup.getDescription() != null && !apiGroup.getDescription().isEmpty()
                        ? apiGroup.getDescription() : "默认分组";

                Item groupFolder = folderMap.computeIfAbsent(groupName, k -> {
                    Item folder = new Item();
                    folder.setName(groupName);
                    folder.setDescription("API分组: " + groupName);
                    return folder;
                });

                for (MethodInfo methodInfo : apiGroup.getMethodList()) {
                    if (methodInfo == null) {
                        continue;
                    }

                    // 处理模块路径
                    Item currentFolder = groupFolder;
                    if (methodInfo.getApiPath() != null && !methodInfo.getApiPath().isEmpty()) {
                        String modulePath = String.join("/", methodInfo.getApiPath());
                        if (!modulePath.isEmpty()) {
                            // 查找或创建模块文件夹
                            boolean found = false;
                            for (Item subFolder : currentFolder.getItem()) {
                                if (modulePath.equals(subFolder.getName())) {
                                    currentFolder = subFolder;
                                    found = true;
                                    break;
                                }
                            }

                            if (!found) {
                                Item moduleFolder = new Item();
                                moduleFolder.setName(modulePath);
                                moduleFolder.setDescription("模块: " + modulePath);
                                currentFolder.getItem().add(moduleFolder);
                                currentFolder = moduleFolder;
                            }
                        }
                    }

                    // 为每个HTTP方法创建一个请求
                    for (String method : methodInfo.getMethods()) {
                        Item requestItem = createRequestItem(methodInfo, method);
                        currentFolder.getItem().add(requestItem);
                    }
                }
            }
        }

        // 将所有文件夹添加到集合中
        for (Item folder : folderMap.values()) {
            collection.getItem().add(folder);
        }

        return collection;
    }

    /**
     * 创建请求项
     */
    private static Item createRequestItem(MethodInfo methodInfo, String method) {
        Item item = new Item();

        // 设置请求名称和描述
        item.setName(methodInfo.getName() != null ? methodInfo.getName() : "未命名请求");
        item.setDescription(methodInfo.getDescription() != null ? methodInfo.getDescription() : "");

        // 创建请求对象
        Request request = new Request();
        request.setMethod(method);
        request.setDescription(methodInfo.getDescription() != null ? methodInfo.getDescription() : "");

        // 设置请求头
        Header header = new Header();
        List<HeaderItem> headerItems = new ArrayList<>();

        if (methodInfo.getMethodHeaders() != null) {
            for (MethodHeader methodHeader : methodInfo.getMethodHeaders()) {
                if (methodHeader == null) {
                    continue;
                }

                HeaderItem headerItem = new HeaderItem();
                headerItem.setKey(methodHeader.getKey());
                headerItem.setValue(methodHeader.getValue());
                headerItem.setDescription(methodHeader.getDescription());
                headerItem.setType("text");
                headerItems.add(headerItem);
            }
        }

        // 添加默认的Content-Type头
        HeaderItem contentTypeHeader = new HeaderItem();
        contentTypeHeader.setKey("Content-Type");
        contentTypeHeader.setValue("application/json");
        contentTypeHeader.setDescription("内容类型");
        contentTypeHeader.setType("text");
        headerItems.add(contentTypeHeader);

        header.setHeaderItems(headerItems);
        request.setHeader(header);

        // 设置URL
        Url url = new Url();

        // 构建路径
        StringBuilder pathBuilder = new StringBuilder();
        List<String> pathSegments = new ArrayList<>();

        // 添加API路径前缀
        if (methodInfo.getApiPath() != null && !methodInfo.getApiPath().isEmpty()) {
            for (String segment : methodInfo.getApiPath()) {
                if (segment != null && !segment.isEmpty()) {
                    pathBuilder.append("/").append(segment);
                    pathSegments.add(segment);
                }
            }
        }

        // 添加路径段
        if (methodInfo.getPaths() != null && !methodInfo.getPaths().isEmpty()) {
            for (String segment : methodInfo.getPaths()) {
                if (segment != null && !segment.isEmpty()) {
                    pathBuilder.append("/").append(segment);
                    pathSegments.add(segment);
                }
            }
        }

        String path = pathBuilder.toString();
        if (path.isEmpty()) {
            path = "/";
        }

        url.setRaw("{{baseUrl}}" + path);
        url.setHost(Collections.singletonList("{{baseUrl}}"));
        url.setPath(pathSegments);

        // 添加查询参数
        List<QueryParam> queryParams = new ArrayList<>();

        if (methodInfo.getRequest() != null) {
            for (RequestParam requestParam : methodInfo.getRequest()) {
                if (requestParam == null || requestParam.getParameters() == null) {
                    continue;
                }

                for (ParamInfo paramInfo : requestParam.getParameters()) {
                    if (paramInfo == null) {
                        continue;
                    }

                    QueryParam queryParam = new QueryParam();
                    queryParam.setKey(paramInfo.getName());
                    queryParam.setValue("");
                    queryParam.setDescription(paramInfo.getValue());
                    queryParams.add(queryParam);
                }
            }
        }

        url.setQuery(queryParams);
        request.setUrl(url);

        // 设置请求体（对于POST、PUT等方法）
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            Body body = new Body();
            body.setMode("raw");

            // 创建一个示例请求体
            StringBuilder rawBody = new StringBuilder("{\n");

            if (methodInfo.getRequest() != null) {
                for (RequestParam requestParam : methodInfo.getRequest()) {
                    if (requestParam == null || requestParam.getParameters() == null) {
                        continue;
                    }

                    for (ParamInfo paramInfo : requestParam.getParameters()) {
                        if (paramInfo == null) {
                            continue;
                        }

                        rawBody.append("    \"").append(paramInfo.getName()).append("\": ");

                        // 根据数据类型生成示例值
                        String dataType = paramInfo.getDataType();
                        if (dataType == null) {
                            rawBody.append("\"\"");
                        } else if (dataType.equalsIgnoreCase("String")) {
                            rawBody.append("\"示例文本\"");
                        } else if (dataType.equalsIgnoreCase("Integer") ||
                                   dataType.equalsIgnoreCase("Long") ||
                                   dataType.equalsIgnoreCase("int")) {
                            rawBody.append("0");
                        } else if (dataType.equalsIgnoreCase("Float") ||
                                   dataType.equalsIgnoreCase("Double") ||
                                   dataType.equalsIgnoreCase("Decimal")) {
                            rawBody.append("0.0");
                        } else if (dataType.equalsIgnoreCase("Boolean")) {
                            rawBody.append("false");
                        } else if (dataType.equalsIgnoreCase("Date") ||
                                   dataType.equalsIgnoreCase("DateTime") ||
                                   dataType.equalsIgnoreCase("LocalDateTime") ||
                                   dataType.equalsIgnoreCase("LocalDate")) {
                            rawBody.append("\"2023-01-01T00:00:00\"");
                        } else {
                            rawBody.append("\"\"");
                        }

                        rawBody.append(",\n");
                    }
                }
            }

            // 移除最后一个逗号
            if (rawBody.toString().endsWith(",\n")) {
                rawBody.delete(rawBody.length() - 2, rawBody.length());
                rawBody.append("\n");
            }

            rawBody.append("}");
            body.setRaw(rawBody.toString());

            // 设置选项
            Options options = new Options();
            Raw raw = new Raw();
            raw.setLanguage("json");
            options.setRaw(raw);
            body.setOptions(options);

            request.setBody(body);
        }

        item.setRequest(request);

        // 添加示例响应
        List<ApiToPostmanConverterModels.Response> responses = new ArrayList<>();

        if (methodInfo.getResponse() != null && !methodInfo.getResponse().isEmpty()) {
            ApiToPostmanConverterModels.Response response = new ApiToPostmanConverterModels.Response();
            response.setName("成功响应");
            response.setStatus("OK");
            response.setCode(200);
            response.set_postman_previewlanguage("json");

            // 创建一个示例响应体
            StringBuilder responseBody = new StringBuilder("{\n");

            for (ResponseParam responseParam : methodInfo.getResponse()) {
                if (responseParam == null || responseParam.getParameters() == null) {
                    continue;
                }

                for (ParamInfo paramInfo : responseParam.getParameters()) {
                    if (paramInfo == null) {
                        continue;
                    }

                    responseBody.append("    \"").append(paramInfo.getName()).append("\": ");

                    // 处理嵌套对象
                    if ("OBJECT".equals(paramInfo.getModelType()) && paramInfo.getChildren() != null) {
                        responseBody.append("{\n");

                        if (paramInfo.getChildren().getParameters() != null) {
                            for (ParamInfo childParam : paramInfo.getChildren().getParameters()) {
                                if (childParam == null) {
                                    continue;
                                }

                                responseBody.append("        \"").append(childParam.getName()).append("\": ");

                                // 根据数据类型生成示例值
                                String dataType = childParam.getDataType();
                                if (dataType == null) {
                                    responseBody.append("\"\"");
                                } else if (dataType.equalsIgnoreCase("String")) {
                                    responseBody.append("\"示例文本\"");
                                } else if (dataType.equalsIgnoreCase("Integer") ||
                                           dataType.equalsIgnoreCase("Long") ||
                                           dataType.equalsIgnoreCase("int")) {
                                    responseBody.append("0");
                                } else if (dataType.equalsIgnoreCase("Float") ||
                                           dataType.equalsIgnoreCase("Double") ||
                                           dataType.equalsIgnoreCase("Decimal")) {
                                    responseBody.append("0.0");
                                } else if (dataType.equalsIgnoreCase("Boolean")) {
                                    responseBody.append("false");
                                } else if (dataType.equalsIgnoreCase("Date") ||
                                           dataType.equalsIgnoreCase("DateTime") ||
                                           dataType.equalsIgnoreCase("LocalDateTime") ||
                                           dataType.equalsIgnoreCase("LocalDate")) {
                                    responseBody.append("\"2023-01-01T00:00:00\"");
                                } else {
                                    responseBody.append("\"\"");
                                }

                                responseBody.append(",\n");
                            }

                            // 移除最后一个逗号
                            if (responseBody.toString().endsWith(",\n")) {
                                responseBody.delete(responseBody.length() - 2, responseBody.length());
                                responseBody.append("\n");
                            }
                        }

                        responseBody.append("    }");
                    } else {
                        // 根据数据类型生成示例值
                        String dataType = paramInfo.getDataType();
                        if (dataType == null) {
                            responseBody.append("\"\"");
                        } else if (dataType.equalsIgnoreCase("String")) {
                            responseBody.append("\"示例文本\"");
                        } else if (dataType.equalsIgnoreCase("Integer") ||
                                   dataType.equalsIgnoreCase("Long") ||
                                   dataType.equalsIgnoreCase("int")) {
                            responseBody.append("0");
                        } else if (dataType.equalsIgnoreCase("Float") ||
                                   dataType.equalsIgnoreCase("Double") ||
                                   dataType.equalsIgnoreCase("Decimal")) {
                            responseBody.append("0.0");
                        } else if (dataType.equalsIgnoreCase("Boolean")) {
                            responseBody.append("false");
                        } else if (dataType.equalsIgnoreCase("Date") ||
                                   dataType.equalsIgnoreCase("DateTime") ||
                                   dataType.equalsIgnoreCase("LocalDateTime") ||
                                   dataType.equalsIgnoreCase("LocalDate")) {
                            responseBody.append("\"2023-01-01T00:00:00\"");
                        } else {
                            responseBody.append("\"\"");
                        }
                    }

                    responseBody.append(",\n");
                }
            }

            // 移除最后一个逗号
            if (responseBody.toString().endsWith(",\n")) {
                responseBody.delete(responseBody.length() - 2, responseBody.length());
                responseBody.append("\n");
            }

            responseBody.append("}");
            response.setBody(responseBody.toString());

            responses.add(response);
        }

        item.setResponse(responses);

        return item;
    }
}
