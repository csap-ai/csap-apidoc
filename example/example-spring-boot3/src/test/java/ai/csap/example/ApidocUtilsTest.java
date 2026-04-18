package ai.csap.example;

import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.util.ApidocUtils;
import ai.csap.example.controller.ProductApi;
import ai.csap.example.controller.ProductController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 ApidocUtils 的参数注解继承功能
 */
public class ApidocUtilsTest {

    @Test
    public void testGetParameterAnnotationFromInterface() throws Exception {
        // 获取实现类的方法
        Method implMethod = ProductController.class.getMethod(
                "listProducts",
                Integer.class,
                Integer.class,
                Long.class,
                BigDecimal.class,
                BigDecimal.class,
                Boolean.class
        );

        // 获取接口的方法
        Method interfaceMethod = ProductApi.class.getMethod(
                "listProducts",
                Integer.class,
                Integer.class,
                Long.class,
                BigDecimal.class,
                BigDecimal.class,
                Boolean.class
        );

        System.out.println("=== 测试 ApidocUtils.getParameterAnnotation ===");
        System.out.println();

        // 测试第一个参数 (page)
        System.out.println("--- 测试第一个参数 (page) ---");
        
        // 方式1: 使用 Method + 索引
        ApiProperty apiProperty1 = ApidocUtils.getParameterAnnotation(implMethod, 0, ApiProperty.class);
        System.out.println("方式1 (Method + 索引): " + (apiProperty1 != null ? apiProperty1.value() : "null"));
        
        // 方式2: 使用 Parameter 对象
        Parameter parameter1 = implMethod.getParameters()[0];
        ApiProperty apiProperty2 = ApidocUtils.getParameterAnnotation(parameter1, ApiProperty.class);
        System.out.println("方式2 (Parameter): " + (apiProperty2 != null ? apiProperty2.value() : "null"));
        
        // 直接从接口方法获取（对比）
        ApiProperty interfaceApiProperty = interfaceMethod.getParameters()[0].getAnnotation(ApiProperty.class);
        System.out.println("接口方法直接获取: " + (interfaceApiProperty != null ? interfaceApiProperty.value() : "null"));
        
        // 直接从实现类方法获取（对比）
        ApiProperty implApiProperty = implMethod.getParameters()[0].getAnnotation(ApiProperty.class);
        System.out.println("实现类方法直接获取: " + (implApiProperty != null ? implApiProperty.value() : "null"));
        
        System.out.println();

        // 测试所有参数
        System.out.println("--- 测试所有参数 ---");
        String[] expectedValues = {"页码", "每页数量", "分类ID", "最低价格", "最高价格", "是否在售"};
        
        for (int i = 0; i < 6; i++) {
            Parameter param = implMethod.getParameters()[i];
            
            // 使用 Method + 索引
            ApiProperty ap1 = ApidocUtils.getParameterAnnotation(implMethod, i, ApiProperty.class);
            
            // 使用 Parameter
            ApiProperty ap2 = ApidocUtils.getParameterAnnotation(param, ApiProperty.class);
            
            System.out.println("参数 " + i + " (" + param.getName() + "):");
            System.out.println("  Method+索引: " + (ap1 != null ? ap1.value() : "null"));
            System.out.println("  Parameter:   " + (ap2 != null ? ap2.value() : "null"));
            System.out.println("  期望值:      " + expectedValues[i]);
            
            // 断言
            assertNotNull(ap1, "参数 " + i + " 使用 Method+索引 应该能获取到 @ApiProperty");
            assertNotNull(ap2, "参数 " + i + " 使用 Parameter 应该能获取到 @ApiProperty");
            assertEquals(expectedValues[i], ap1.value(), "参数 " + i + " 的 value 应该是 " + expectedValues[i]);
            assertEquals(expectedValues[i], ap2.value(), "参数 " + i + " 的 value 应该是 " + expectedValues[i]);
            
            System.out.println();
        }

        // 测试 @RequestParam 注解（Spring 注解）
        System.out.println("--- 测试 @RequestParam 注解 ---");
        RequestParam requestParam1 = ApidocUtils.getParameterAnnotation(implMethod, 0, RequestParam.class);
        System.out.println("第一个参数的 @RequestParam: " + (requestParam1 != null ? "defaultValue=" + requestParam1.defaultValue() : "null"));
        
        Parameter param0 = implMethod.getParameters()[0];
        RequestParam requestParam2 = ApidocUtils.getParameterAnnotation(param0, RequestParam.class);
        System.out.println("使用 Parameter 获取 @RequestParam: " + (requestParam2 != null ? "defaultValue=" + requestParam2.defaultValue() : "null"));
        
        assertNotNull(requestParam1, "应该能从接口继承 @RequestParam 注解");
        assertNotNull(requestParam2, "使用 Parameter 应该能从接口继承 @RequestParam 注解");
        assertEquals("1", requestParam1.defaultValue());
        assertEquals("1", requestParam2.defaultValue());
    }

    @Test
    public void testHasParameterAnnotation() throws Exception {
        Method implMethod = ProductController.class.getMethod(
                "listProducts",
                Integer.class,
                Integer.class,
                Long.class,
                BigDecimal.class,
                BigDecimal.class,
                Boolean.class
        );

        System.out.println("=== 测试 ApidocUtils.hasParameterAnnotation ===");
        System.out.println();

        // 测试 Method + 索引
        boolean has1 = ApidocUtils.hasParameterAnnotation(implMethod, 0, ApiProperty.class);
        System.out.println("Method+索引 方式: " + has1);
        assertTrue(has1, "应该检测到 @ApiProperty 注解");

        // 测试 Parameter
        Parameter param = implMethod.getParameters()[0];
        boolean has2 = ApidocUtils.hasParameterAnnotation(param, ApiProperty.class);
        System.out.println("Parameter 方式: " + has2);
        assertTrue(has2, "应该检测到 @ApiProperty 注解");
    }
}

