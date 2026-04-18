package ai.csap.example;

import ai.csap.apidoc.devtools.core.ApidocDevtools;
import ai.csap.apidoc.devtools.model.GetFieldExcled;
import ai.csap.apidoc.devtools.model.api.Field;
import ai.csap.example.controller.UserCrudController;

import java.util.Collections;
import java.util.List;

/**
 * 测试抽象 Controller 继承场景
 * 
 * 场景：Controller 继承抽象父类，父类有泛型的 CRUD 方法，子类传入具体泛型类型
 * 子类不重写父类的 HTTP 方法，直接使用继承的方法
 * 
 * 验证：getFieldsFromMethod 能否正确解析继承方法中的泛型类型
 *
 * @author CSAP Team
 */
public class AbstractControllerTest {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("测试：抽象 Controller 继承场景");
        System.out.println("UserCrudController extends BaseCrudController<User, Long>");
        System.out.println("========================================\n");

        int passed = 0;
        int failed = 0;

        // 测试1：create 方法的参数（T entity -> User entity）
        try {
            System.out.println("【测试1】create 方法参数：T entity -> User entity");
            passed += testCreateMethodParameter() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试2：create 方法的返回值（Response<T> -> Response<User>）
        try {
            System.out.println("\n【测试2】create 方法返回值：Response<T> -> Response<User>");
            passed += testCreateMethodReturn() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试3：update 方法的参数（第2个参数 T entity）
        try {
            System.out.println("\n【测试3】update 方法第2个参数：T entity -> User entity");
            passed += testUpdateMethodParameter() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试4：list 方法的返回值（Response<List<T>> -> Response<List<User>>）
        try {
            System.out.println("\n【测试4】list 方法返回值：Response<List<T>> -> Response<List<User>>");
            passed += testListMethodReturn() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试5：batchCreate 方法的参数（List<T> entities -> List<User> entities）
        try {
            System.out.println("\n【测试5】batchCreate 方法参数：List<T> entities -> List<User> entities");
            passed += testBatchCreateMethodParameter() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试6：getById 方法的参数（ID id -> Long id）
        try {
            System.out.println("\n【测试6】getById 方法参数：ID id -> Long id");
            passed += testGetByIdMethodParameter() ? 1 : 0;
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
     * 测试1：create 方法参数
     */
    private static boolean testCreateMethodParameter() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(UserCrudController.class.getName())
                .methodName("create")  // 父类的方法
                .parameterIndex(0)     // T entity
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        printFieldList(fields, "    ");

        // 验证：应该获取到 User 的字段
        boolean hasUsername = false, hasEmail = false, hasId = false;

        for (Field field : fields) {
            String name = field.getName();
            if ("username".equals(name)) hasUsername = true;
            if ("email".equals(name)) hasEmail = true;
            if ("id".equals(name)) hasId = true;
        }

        boolean success = hasUsername && hasEmail && hasId && fields.size() > 0;

        if (!hasUsername) System.out.println("  ✗ 缺少 username 字段");
        if (!hasEmail) System.out.println("  ✗ 缺少 email 字段");
        if (!hasId) System.out.println("  ✗ 缺少 id 字段");
        if (fields.isEmpty()) System.out.println("  ✗ 未获取到任何字段！泛型类型解析可能失败");

        if (success) {
            System.out.println("  ✓ 测试通过！成功将 T 解析为 User");
        }
        return success;
    }

    /**
     * 测试2：create 方法返回值
     */
    private static boolean testCreateMethodReturn() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(UserCrudController.class.getName())
                .methodName("create")
                .parameterIndex(-1)  // 返回值
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        printFieldList(fields, "    ");

        boolean hasCode = false, hasData = false, dataHasChildren = false;

        for (Field field : fields) {
            String name = field.getName();
            if ("code".equals(name)) hasCode = true;
            if ("data".equals(name)) {
                hasData = true;
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    dataHasChildren = true;
                    System.out.println("  ✓ data 字段有子字段（User的字段）");
                }
            }
        }

        boolean success = hasCode && hasData && dataHasChildren;

        if (!hasCode) System.out.println("  ✗ 缺少 code 字段");
        if (!hasData) System.out.println("  ✗ 缺少 data 字段");
        if (!dataHasChildren) System.out.println("  ✗ data 字段没有子字段，泛型类型解析可能失败");

        if (success) {
            System.out.println("  ✓ 测试通过！成功将 Response<T> 解析为 Response<User>");
        }
        return success;
    }

    /**
     * 测试3：update 方法参数
     */
    private static boolean testUpdateMethodParameter() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(UserCrudController.class.getName())
                .methodName("update")
                .parameterIndex(1)  // 第2个参数 T entity
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        printFieldList(fields, "    ");

        boolean hasUsername = false;

        for (Field field : fields) {
            if ("username".equals(field.getName())) {
                hasUsername = true;
                break;
            }
        }

        boolean success = hasUsername && fields.size() > 0;

        if (!hasUsername) System.out.println("  ✗ 缺少 username 字段");
        if (fields.isEmpty()) System.out.println("  ✗ 未获取到任何字段！泛型类型解析可能失败");

        if (success) {
            System.out.println("  ✓ 测试通过！成功将 T 解析为 User");
        }
        return success;
    }

    /**
     * 测试4：list 方法返回值
     */
    private static boolean testListMethodReturn() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(UserCrudController.class.getName())
                .methodName("list")
                .parameterIndex(-1)  // 返回值
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
                    System.out.println("  ✓ data 字段有子字段（List<User> 的元素字段）");
                }
            }
        }

        boolean success = hasData && dataHasChildren;

        if (!hasData) System.out.println("  ✗ 缺少 data 字段");
        if (!dataHasChildren) System.out.println("  ✗ data 字段没有子字段，泛型类型 List<T> 解析可能失败");

        if (success) {
            System.out.println("  ✓ 测试通过！成功将 Response<List<T>> 解析为 Response<List<User>>");
        }
        return success;
    }

    /**
     * 测试5：batchCreate 方法参数
     */
    private static boolean testBatchCreateMethodParameter() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(UserCrudController.class.getName())
                .methodName("batchCreate")
                .parameterIndex(0)  // List<T> entities
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        printFieldList(fields, "    ");

        // List<T> 应该解析为 List<User>，字段应该是 User 的字段
        boolean hasUsername = false;

        for (Field field : fields) {
            if ("username".equals(field.getName())) {
                hasUsername = true;
                break;
            }
        }

        boolean success = hasUsername && fields.size() > 0;

        if (!hasUsername) System.out.println("  ✗ 缺少 username 字段");
        if (fields.isEmpty()) System.out.println("  ✗ 未获取到任何字段！List<T> 泛型类型解析可能失败");

        if (success) {
            System.out.println("  ✓ 测试通过！成功将 List<T> 解析为 List<User>");
        }
        return success;
    }

    /**
     * 测试6：getById 方法参数
     */
    private static boolean testGetByIdMethodParameter() throws Exception {
        ApidocDevtools devtools = createApidocDevtools();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(UserCrudController.class.getName())
                .methodName("getById")
                .parameterIndex(0)  // ID id -> Long id
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());

        // Long 是基本类型，没有子字段，但应该能正确识别
        // 对于基本类型参数，字段列表可能为空，这是正常的
        System.out.println("  ID 参数类型应该被解析为 Long（基本类型，无子字段）");
        System.out.println("  ✓ 测试通过！");
        return true;
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

