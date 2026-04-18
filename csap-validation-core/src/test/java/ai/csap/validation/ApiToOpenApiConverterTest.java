package ai.csap.validation;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * API转OpenAPI转换器测试类
 */
public class ApiToOpenApiConverterTest {

    private static final String PATH = "/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/csap-framework-validation-core/src/test/resources/";

    @Test
    public void testConvertApiToOpenApi() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 读取api.json文件
            ApiToOpenApiConverterModels.ApiListData apiListData = objectMapper.readValue(
                    new File(PATH + "api.json"), ApiToOpenApiConverterModels.ApiListData.class);

            // 构建OpenAPI对象
            ApiToOpenApiConverterModels.OpenAPI openAPI = ApiToOpenApiConverter.buildOpenAPI(apiListData);

            // 将OpenAPI对象写入文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    new File(PATH + "openapi_test.json"), openAPI);

            System.out.println("测试转换完成，已生成openapi_test.json文件");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
