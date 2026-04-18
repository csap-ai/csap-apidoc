package ai.csap.example;

import ai.csap.apidoc.devtools.core.ApidocDevtools;
import ai.csap.apidoc.devtools.model.GetFieldExcled;
import ai.csap.apidoc.devtools.model.api.Field;
import ai.csap.example.controller.GenericTestController;

import java.util.Collections;
import java.util.List;

/**
 * 测试 getFieldsFromMethod 对泛型场景的支持
 *
 * 测试用例：
 * 1. 继承泛型父类的请求参数（UserRequest extends BaseRequest<User>）
 * 2. 直接使用泛型类型参数（BaseRequest<Product>）
 * 3. 泛型返回值（Response<List<User>>）
 * 4. 多参数场景，获取指定索引的参数
 *
 * @author CSAP Team
 */
public class GenericFieldsTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("测试 getFieldsFromMethod 泛型场景支持");
        System.out.println("========================================\n");

        int passed = 0;
        int failed = 0;

        // 测试1：继承泛型父类的请求参数
        try {
            System.out.println("【测试1】继承泛型父类：UserRequest extends BaseRequest<User>");
            passed += testInheritedGenericRequest() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试2：直接使用泛型类型参数
        try {
            System.out.println("\n【测试2】直接泛型参数：BaseRequest<Product>");
            passed += testDirectGenericParameter() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试3：泛型返回值
        try {
            System.out.println("\n【测试3】泛型返回值：Response<List<User>>");
            passed += testGenericReturnType() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试4：多参数场景
        try {
            System.out.println("\n【测试4】多参数场景：获取第2个参数（index=1）");
            passed += testMultipleParameters() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 总结
        System.out.println("\n========================================");
        System.out.println("测试总结：");
        System.out.println("  通过: " + passed);
        System.out.println("  失败: " + failed);
        System.out.println("========================================");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.out.println("\n✓ 所有测试通过！");
        }
    }

    /**
     * 测试1：继承泛型父类的请求参数
     */
    private static boolean testInheritedGenericRequest() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(GenericTestController.class.getName())
                .methodName("testInheritedGeneric")
                .parameterIndex(0)
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        printFieldList(fields, "    ");

        // 验证
        boolean hasData = false, hasDataList = false, hasRequestId = false;
        boolean hasRemark = false, hasBatch = false;
        boolean dataHasChildren = false, dataListHasChildren = false;

        for (Field field : fields) {
            String name = field.getName();

            if ("data".equals(name)) {
                hasData = true;
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    dataHasChildren = true;
                    System.out.println("  ✓ data 字段有子字段（User的字段）");
                }
            }
            if ("dataList".equals(name)) {
                hasDataList = true;
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    dataListHasChildren = true;
                    System.out.println("  ✓ dataList 字段有子字段（User的字段）");
                }
            }
            if ("requestId".equals(name)) hasRequestId = true;
            if ("remark".equals(name)) hasRemark = true;
            if ("batch".equals(name)) hasBatch = true;
        }

        boolean success = true;
        if (!hasData) {
            System.out.println("  ✗ 缺少继承的 data 字段");
            success = false;
        }
        if (!dataHasChildren) {
            System.out.println("  ✗ data 字段的泛型类型没有正确解析为 User");
            success = false;
        }
        if (!hasDataList) {
            System.out.println("  ✗ 缺少继承的 dataList 字段");
            success = false;
        }
        if (!dataListHasChildren) {
            System.out.println("  ✗ dataList 字段的泛型类型没有正确解析为 List<User>");
            success = false;
        }
        if (!hasRequestId) {
            System.out.println("  ✗ 缺少继承的 requestId 字段");
            success = false;
        }
        if (!hasRemark) {
            System.out.println("  ✗ 缺少子类的 remark 字段");
            success = false;
        }
        if (!hasBatch) {
            System.out.println("  ✗ 缺少子类的 batch 字段");
            success = false;
        }

        if (success) {
            System.out.println("  ✓ 测试通过！");
        }
        return success;
    }

    /**
     * 测试2：直接使用泛型类型参数
     */
    private static boolean testDirectGenericParameter() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(GenericTestController.class.getName())
                .methodName("testDirectGeneric")
                .parameterIndex(0)
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        printFieldList(fields, "    ");

        boolean hasData = false, dataHasChildren = false;

        for (Field field : fields) {
            if ("data".equals(field.getName())) {
                hasData = true;
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    dataHasChildren = true;
                    System.out.println("  ✓ data 字段有子字段（Product的字段）");
                }
            }
        }

        boolean success = hasData && dataHasChildren;
        if (!hasData) {
            System.out.println("  ✗ 缺少 data 字段");
        }
        if (!dataHasChildren) {
            System.out.println("  ✗ data 字段的泛型类型没有正确解析为 Product");
        }

        if (success) {
            System.out.println("  ✓ 测试通过！");
        }
        return success;
    }

    /**
     * 测试3：泛型返回值
     */
    private static boolean testGenericReturnType() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(GenericTestController.class.getName())
                .methodName("testGenericReturn")
                .parameterIndex(-1)  // -1 表示获取返回值
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        printFieldList(fields, "    ");

        boolean hasCode = false, hasMessage = false, hasData = false;
        boolean dataHasChildren = false;

        for (Field field : fields) {
            String name = field.getName();
            if ("code".equals(name)) hasCode = true;
            if ("message".equals(name)) hasMessage = true;
            if ("data".equals(name)) {
                hasData = true;
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    dataHasChildren = true;
                    System.out.println("  ✓ data 字段有子字段（User的字段）");
                }
            }
        }

        boolean success = hasCode && hasMessage && hasData && dataHasChildren;
        if (!hasCode) System.out.println("  ✗ 缺少 code 字段");
        if (!hasMessage) System.out.println("  ✗ 缺少 message 字段");
        if (!hasData) System.out.println("  ✗ 缺少 data 字段");
        if (!dataHasChildren) System.out.println("  ✗ data 字段的泛型类型没有正确解析");

        if (success) {
            System.out.println("  ✓ 测试通过！");
        }
        return success;
    }

    /**
     * 测试4：多参数场景
     */
    private static boolean testMultipleParameters() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        // 获取第2个参数（index=1），即 UserRequest
        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(GenericTestController.class.getName())
                .methodName("testMultiParams")
                .parameterIndex(1)  // 第2个参数
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        printFieldList(fields, "    ");

        boolean hasData = false, hasRemark = false;

        for (Field field : fields) {
            if ("data".equals(field.getName())) hasData = true;
            if ("remark".equals(field.getName())) hasRemark = true;
        }

        boolean success = hasData && hasRemark;
        if (!hasData) System.out.println("  ✗ 缺少继承的 data 字段");
        if (!hasRemark) System.out.println("  ✗ 缺少子类的 remark 字段");

        if (success) {
            System.out.println("  ✓ 测试通过！");
        }
        return success;
    }

    /**
     * 创建 ApidocDevtools 实例
     */
    private static ApidocDevtools createApidocDevtools() throws Exception {
        java.lang.reflect.Constructor<ApidocDevtools> constructor =
                ApidocDevtools.class.getDeclaredConstructor(
                        ai.csap.apidoc.autoconfigure.EnableApidocConfig.class,
                        org.springframework.context.ApplicationContext.class,
                        ai.csap.apidoc.properties.CsapDocConfig.class
                );
        constructor.setAccessible(true);
        return constructor.newInstance(null, null, null);
    }

    /**
     * 打印字段列表
     */
    @SuppressWarnings("unchecked")
    private static void printFieldList(List<Field> fields, String prefix) {
        if (fields == null || fields.isEmpty()) {
            System.out.println(prefix + "(无字段)");
            return;
        }

        for (Field field : fields) {
            System.out.println(prefix + "- " + field.getName() + ": " + field.getDataType()
                    + " [" + field.getFinalClassName() + "]");

            if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                List<Field> children = (List<Field>) field.getChildrenField();
                printFieldList(children, prefix + "  ");
            }
        }
    }
}

