package ai.csap.validation;

import static ai.csap.validation.ApiToOpenApiConverterModels.ApiListData;
import static ai.csap.validation.ApiToOpenApiConverterModels.OpenAPI;
import static ai.csap.validation.ApiToPostmanConverterModels.PostmanCollection;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * API格式转换器，支持将API转换为多种格式
 */
public class ApiFormatConverter {
    public static final String PATH = "/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/csap-framework-validation-core/src/test/resources/";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("请指定转换格式: openapi 或 postman");
            return;
        }

        String format = args[0].toLowerCase();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 读取api.json文件
            ApiListData apiListData = objectMapper.readValue(
                    new File(PATH + "api.json"), ApiListData.class);

            switch (format) {
                case "openapi":
                    convertToOpenApi(objectMapper, apiListData);
                    break;
                case "postman":
                    convertToPostman(objectMapper, apiListData);
                    break;
                default:
                    System.out.println("不支持的格式: " + format + "，请指定 openapi 或 postman");
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 转换为OpenAPI格式
     */
    public static void convertToOpenApi(ObjectMapper objectMapper, ApiListData apiListData) throws IOException {
        // 构建OpenAPI对象
        OpenAPI openAPI = ApiToOpenApiConverter.buildOpenAPI(apiListData);

        // 将OpenAPI对象写入文件
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                new File(PATH + "openapi_generated.json"), openAPI);

        System.out.println("转换完成，已生成openapi_generated.json文件");
    }

    /**
     * 转换为Postman集合格式
     */
    public static void convertToPostman(ObjectMapper objectMapper, ApiListData apiListData) throws IOException {
        // 构建Postman集合对象
        PostmanCollection postmanCollection = ApiToPostmanConverter.buildPostmanCollection(apiListData);

        // 将Postman集合对象写入文件
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                new File(PATH + "postman_collection.json"), postmanCollection);

        System.out.println("转换完成，已生成postman_collection.json文件");
    }
}
