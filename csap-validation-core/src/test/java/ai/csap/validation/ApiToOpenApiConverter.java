package ai.csap.validation;

import static ai.csap.validation.ApiToOpenApiConverterModels.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 将自定义API格式转换为OpenAPI 3.0标准格式的转换器
 */
public class ApiToOpenApiConverter {
    public static final String PATH = "/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/csap-framework-validation-core/src/test/resources/";

    public static void main(String[] args) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 读取api.json文件
            ApiListData apiListData = objectMapper.readValue(
                    new File(PATH + "api.json"), ApiListData.class);

            // 构建OpenAPI对象
            OpenAPI openAPI = buildOpenAPI(apiListData);

            // 将OpenAPI对象写入文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    new File(PATH + "openapi_generated.json"), openAPI);

            System.out.println("转换完成，已生成openapi_generated.json文件");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 构建OpenAPI对象
     */
    public static OpenAPI buildOpenAPI(ApiListData apiListData) {
        OpenAPI openAPI = new OpenAPI();

        // 设置基本信息
        ApiInfo apiInfo = apiListData.getApiInfo();
        if (apiInfo != null) {
            openAPI.setInfo(new Info()
                    .title(apiInfo.getTitle())
                    .description(apiInfo.getDescription())
                    .version(apiInfo.getVersion()));

            // 设置许可证信息
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
            if ("OAUTH_2".equals(apiInfo.getAuthorizationType()) ||
                    apiInfo.getAuthorizationType().startsWith("Authorization Bearer")) {
                openAPI.setComponents(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type("http")
                                .scheme("bearer")
                                .bearerFormat("JWT")));
                openAPI.setSecurity(Collections.singletonList(
                        new SecurityRequirement().addList("bearerAuth")));
            }
        }

        // 收集所有标签
        List<Tag> tags = new ArrayList<>();
        Map<String, String> tagDescriptions = new HashMap<>();

        // 处理API路径
        Map<String, PathItem> paths = new HashMap<>();
        if (apiListData.getApiList() != null) {
            for (ApiGroup apiGroup : apiListData.getApiList()) {
                if (apiGroup == null || apiGroup.getMethodList() == null) {
                    continue;
                }

                // 为每个API组创建一个标签
                String groupName = apiGroup.getDescription() != null && !apiGroup.getDescription().isEmpty()
                        ? apiGroup.getDescription() : "默认分组";
                tagDescriptions.putIfAbsent(groupName, "API分组: " + groupName);

                for (MethodInfo methodInfo : apiGroup.getMethodList()) {
                    if (methodInfo == null) {
                        continue;
                    }

                    // 构建路径
                    String path = buildPath(methodInfo);
                    PathItem pathItem = paths.computeIfAbsent(path, k -> new PathItem());

                    // 处理HTTP方法
                    for (String method : methodInfo.getMethods()) {
                        Operation operation = createOperation(methodInfo);

                        // 添加标签，表示API所属的分组
                        operation.addTagsItem(groupName);

                        // 如果有模块路径，也添加为标签
                        if (methodInfo.getApiPath() != null && !methodInfo.getApiPath().isEmpty()) {
                            String modulePath = String.join("/", methodInfo.getApiPath());
                            if (!modulePath.isEmpty()) {
                                operation.addTagsItem(modulePath);
                                tagDescriptions.putIfAbsent(modulePath, "模块: " + modulePath);
                            }
                        }

                        // 添加请求参数
                        addParameters(operation, methodInfo);

                        // 添加响应
                        addResponses(operation, methodInfo);

                        // 设置操作到路径项
                        switch (method.toUpperCase()) {
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
                                System.out.println("不支持的HTTP方法: " + method);
                                break;
                        }
                    }
                }
            }
        }

        // 添加所有收集到的标签
        for (Map.Entry<String, String> entry : tagDescriptions.entrySet()) {
            tags.add(new Tag().name(entry.getKey()).description(entry.getValue()));
        }
        openAPI.setTags(tags);

        openAPI.setPaths(paths);
        return openAPI;
    }

    /**
     * 构建API路径
     * 考虑API的目录层次结构
     */
    private static String buildPath(MethodInfo methodInfo) {
        StringBuilder pathBuilder = new StringBuilder();

        // 添加API路径前缀（模块/目录）
        if (methodInfo.getApiPath() != null && !methodInfo.getApiPath().isEmpty()) {
            for (String segment : methodInfo.getApiPath()) {
                if (segment != null && !segment.isEmpty()) {
                    pathBuilder.append("/").append(segment);
                }
            }
        }

        // 添加路径段（具体接口路径）
        if (methodInfo.getPaths() != null && !methodInfo.getPaths().isEmpty()) {
            for (String segment : methodInfo.getPaths()) {
                if (segment != null && !segment.isEmpty()) {
                    pathBuilder.append("/").append(segment);
                }
            }
        }

        // 如果路径为空，使用默认路径
        if (pathBuilder.length() == 0) {
            return "/";
        }

        return pathBuilder.toString();
    }

    /**
     * 创建操作对象
     */
    private static Operation createOperation(MethodInfo methodInfo) {
        Operation operation = new Operation()
                .summary(methodInfo.getName())
                .description(methodInfo.getDescription() != null ? methodInfo.getDescription() : "")
                .operationId(methodInfo.getKey());

        return operation;
    }

    /**
     * 添加请求参数
     */
    private static void addParameters(Operation operation, MethodInfo methodInfo) {
        if (methodInfo.getRequest() == null) {
            return;
        }

        for (RequestParam requestParam : methodInfo.getRequest()) {
            if (requestParam == null || requestParam.getParameters() == null) {
                continue;
            }

            for (ParamInfo paramInfo : requestParam.getParameters()) {
                if (paramInfo == null) {
                    continue;
                }

                Parameter parameter = new Parameter()
                        .name(paramInfo.getName())
                        .description(paramInfo.getValue())
                        .required(paramInfo.isRequired())
                        .in("query");  // 默认为query参数

                // 设置参数类型
                Schema schema = new Schema()
                        .type(mapDataType(paramInfo.getDataType()))
                        .description(paramInfo.getValue());

                parameter.setSchema(schema);
                operation.addParametersItem(parameter);
            }
        }
    }

    /**
     * 添加响应
     */
    private static void addResponses(Operation operation, MethodInfo methodInfo) {
        if (methodInfo.getResponse() == null || methodInfo.getResponse().isEmpty()) {
            // 添加默认响应
            operation.addResponsesItem("200", new Response()
                    .description("成功")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType()
                                    .schema(new Schema().type("object")))));
            return;
        }

        for (ResponseParam responseParam : methodInfo.getResponse()) {
            if (responseParam == null) {
                continue;
            }

            // 创建响应内容
            Content content = new Content();
            MediaType mediaType = new MediaType();
            Schema responseSchema = new Schema().type("object");

            // 处理响应参数
            if (responseParam.getParameters() != null) {
                for (ParamInfo paramInfo : responseParam.getParameters()) {
                    if (paramInfo == null) {
                        continue;
                    }

                    // 处理基本参数
                    Schema propSchema = new Schema()
                            .type(mapDataType(paramInfo.getDataType()))
                            .description(paramInfo.getValue());

                    responseSchema.getProperties().put(paramInfo.getName(), propSchema);

                    // 处理嵌套对象
                    if ("OBJECT".equals(paramInfo.getModelType()) && paramInfo.getChildren() != null) {
                        Schema childSchema = new Schema().type("object");

                        if (paramInfo.getChildren().getParameters() != null) {
                            for (ParamInfo childParam : paramInfo.getChildren().getParameters()) {
                                if (childParam == null) {
                                    continue;
                                }

                                Schema childPropSchema = new Schema()
                                        .type(mapDataType(childParam.getDataType()))
                                        .description(childParam.getValue());

                                childSchema.getProperties().put(childParam.getName(), childPropSchema);
                            }
                        }

                        responseSchema.getProperties().put(paramInfo.getName(), childSchema);
                    }
                }
            }

            mediaType.setSchema(responseSchema);
            content.addMediaType("application/json", mediaType);

            // 添加响应
            operation.addResponsesItem("200", new Response()
                    .description("成功")
                    .content(content));
        }
    }

    /**
     * 映射数据类型到OpenAPI类型
     */
    private static String mapDataType(String dataType) {
        if (dataType == null) {
            return "string";
        }

        switch (dataType.toLowerCase()) {
            case "integer":
            case "long":
            case "int":
                return "integer";
            case "float":
            case "double":
            case "decimal":
                return "number";
            case "boolean":
                return "boolean";
            case "date":
            case "datetime":
            case "localdatetime":
            case "localdate":
                return "string";
            default:
                return "string";
        }
    }
}
