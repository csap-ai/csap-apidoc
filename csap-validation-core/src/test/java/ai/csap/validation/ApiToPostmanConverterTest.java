package ai.csap.validation;

import static ai.csap.validation.ApiToOpenApiConverterModels.ApiListData;
import static ai.csap.validation.ApiToPostmanConverterModels.PostmanCollection;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * API转Postman集合转换器测试类
 */
public class ApiToPostmanConverterTest {

    private static final String PATH = "/Users/ycf/Documents/产品/csap/framework/csap-framework-apidoc/csap-framework-validation-core/src/test/resources/";

    @Test
    public void testConvertApiToPostman() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 读取api.json文件
            ApiListData apiListData = objectMapper.readValue(
                    new File(PATH + "api.json"), ApiListData.class);

            // 构建Postman集合对象
            PostmanCollection postmanCollection = ApiToPostmanConverter.buildPostmanCollection(apiListData);

            // 将Postman集合对象写入文件
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    new File(PATH + "postman_test.json"), postmanCollection);

            System.out.println("测试转换完成，已生成postman_test.json文件");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
