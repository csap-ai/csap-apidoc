package ai.csap.apidoc.devtools.core;

import java.util.List;

import ai.csap.apidoc.devtools.model.GetFieldExcled;
import ai.csap.apidoc.devtools.model.api.Field;

/**
 * 测试运行器 - 用于自动化验证泛型字段获取功能
 *
 * @author ycf
 */
public class GenericTypeTestRunner {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("开始自动化测试：泛型字段获取功能");
        System.out.println("========================================\n");

        int passed = 0;
        int failed = 0;

        // 测试1：基本泛型参数测试
        try {
            System.out.println("【测试1】基本泛型参数测试");
            ApidocDevtools devtools = GenericTypeTest.createApidocDevtoolsInstance();

            GetFieldExcled request = GetFieldExcled.builder()
                    .controllerClassName(GenericTypeTest.MockComplexTestController.class.getName())
                    .methodName("genericTest")
                    .parameterIndex(0)
                    .excledFields(java.util.Collections.emptySet())
                    .build();

            List<Field> fields = devtools.getFieldsFromMethod(request);

            if (fields == null || fields.isEmpty()) {
                throw new AssertionError("字段列表不能为空");
            }

            // 验证应该包含 data、dataList、meta 字段
            boolean hasData = false, hasDataList = false, hasMeta = false;
            for (Field field : fields) {
                if ("data".equals(field.getName())) hasData = true;
                if ("dataList".equals(field.getName())) hasDataList = true;
                if ("meta".equals(field.getName())) hasMeta = true;
            }

            if (!hasData || !hasDataList || !hasMeta) {
                throw new AssertionError("缺少必要字段: data=" + hasData + ", dataList=" + hasDataList + ", meta=" + hasMeta);
            }

            // 验证 dataList 有子字段（应该是 MockUserInfo 的字段）
            for (Field field : fields) {
                if ("dataList".equals(field.getName())) {
                    if (field.getChildrenField() == null || field.getChildrenField().isEmpty()) {
                        throw new AssertionError("dataList 字段应该包含子字段（MockUserInfo的字段）");
                    }
                    // 验证子字段中包含 userId、username、email
                    List<Field> children = (List<Field>) field.getChildrenField();
                    boolean hasUserId = false, hasUsername = false, hasEmail = false;
                    for (Field child : children) {
                        if ("userId".equals(child.getName())) hasUserId = true;
                        if ("username".equals(child.getName())) hasUsername = true;
                        if ("email".equals(child.getName())) hasEmail = true;
                    }
                    if (!hasUserId || !hasUsername || !hasEmail) {
                        throw new AssertionError("dataList 的子字段不完整: userId=" + hasUserId + ", username=" + hasUsername + ", email=" + hasEmail);
                    }
                }
            }

            System.out.println("  ✓ 测试通过：成功获取泛型参数字段\n");
            passed++;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试2：继承场景测试
        try {
            System.out.println("【测试2】继承场景测试（父类泛型，子类继承）");
            ApidocDevtools devtools = GenericTypeTest.createApidocDevtoolsInstance();

            GetFieldExcled request = GetFieldExcled.builder()
                    .controllerClassName(GenericTypeTest.MockInheritanceTestController.class.getName())
                    .methodName("inheritedTest")
                    .parameterIndex(0)
                    .excledFields(java.util.Collections.emptySet())
                    .build();

            List<Field> fields = devtools.getFieldsFromMethod(request);

            if (fields == null || fields.isEmpty()) {
                throw new AssertionError("字段列表不能为空");
            }

            // 验证应该包含继承的 data、requestId 字段和子类的 extraInfo 字段
            boolean hasData = false, hasRequestId = false, hasExtraInfo = false;
            for (Field field : fields) {
                if ("data".equals(field.getName())) hasData = true;
                if ("requestId".equals(field.getName())) hasRequestId = true;
                if ("extraInfo".equals(field.getName())) hasExtraInfo = true;
            }

            if (!hasData || !hasRequestId || !hasExtraInfo) {
                throw new AssertionError("缺少必要字段: data=" + hasData + ", requestId=" + hasRequestId + ", extraInfo=" + hasExtraInfo);
            }

            // 验证继承的 data 字段有子字段（应该是 MockUserInfo 的字段）
            for (Field field : fields) {
                if ("data".equals(field.getName())) {
                    if (field.getChildrenField() == null || field.getChildrenField().isEmpty()) {
                        throw new AssertionError("继承的 data 字段应该包含子字段（MockUserInfo的字段）");
                    }
                    List<Field> children = (List<Field>) field.getChildrenField();
                    boolean hasUserId = false, hasUsername = false, hasEmail = false;
                    for (Field child : children) {
                        if ("userId".equals(child.getName())) hasUserId = true;
                        if ("username".equals(child.getName())) hasUsername = true;
                        if ("email".equals(child.getName())) hasEmail = true;
                    }
                    if (!hasUserId || !hasUsername || !hasEmail) {
                        throw new AssertionError("data 的子字段不完整: userId=" + hasUserId + ", username=" + hasUsername + ", email=" + hasEmail);
                    }

                    // 验证字段类型应该是 MockUserInfo
                    String finalClassName = field.getFinalClassName();
                    if (!finalClassName.contains("MockUserInfo")) {
                        throw new AssertionError("data 字段的类型应该是 MockUserInfo，实际是: " + finalClassName);
                    }
                }
            }

            System.out.println("  ✓ 测试通过：成功处理继承场景，泛型类型正确解析\n");
            passed++;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试3：返回值泛型测试
        try {
            System.out.println("【测试3】返回值泛型测试");
            ApidocDevtools devtools = GenericTypeTest.createApidocDevtoolsInstance();

            GetFieldExcled request = GetFieldExcled.builder()
                    .controllerClassName(GenericTypeTest.MockComplexTestController.class.getName())
                    .methodName("genericTest")
                    .parameterIndex(-1)  // -1 表示返回值
                    .excledFields(java.util.Collections.emptySet())
                    .build();

            List<Field> fields = devtools.getFieldsFromMethod(request);

            if (fields == null || fields.isEmpty()) {
                throw new AssertionError("字段列表不能为空");
            }

            // 验证返回值结构
            boolean hasData = false, hasSuccess = false, hasMessage = false;
            for (Field field : fields) {
                if ("data".equals(field.getName())) hasData = true;
                if ("success".equals(field.getName())) hasSuccess = true;
                if ("message".equals(field.getName())) hasMessage = true;
            }

            if (!hasData || !hasSuccess || !hasMessage) {
                throw new AssertionError("返回值缺少必要字段");
            }

            System.out.println("  ✓ 测试通过：成功获取返回值泛型字段\n");
            passed++;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试4：抽象类和接口继承场景测试（CURD场景）
        try {
            System.out.println("【测试4】抽象类和接口继承场景测试（CURD场景）");
            ApidocDevtools devtools = GenericTypeTest.createApidocDevtoolsInstance();

            GetFieldExcled request = GetFieldExcled.builder()
                    .controllerClassName(GenericTypeTest.MockUserController.class.getName())
                    .methodName("create")  // 从抽象父类继承的方法
                    .parameterIndex(0)
                    .excledFields(java.util.Collections.emptySet())
                    .build();

            List<Field> fields = devtools.getFieldsFromMethod(request);

            if (fields == null || fields.isEmpty()) {
                throw new AssertionError("字段列表不能为空");
            }

            // 验证：entity 参数的类型应该是 MockUserInfo，应该包含 userId、username、email 字段
            boolean foundUserId = false, foundUsername = false, foundEmail = false;
            for (Field field : fields) {
                if ("userId".equals(field.getName())) {
                    foundUserId = true;
                    System.out.println("  ✓ 找到 userId 字段");
                }
                if ("username".equals(field.getName())) {
                    foundUsername = true;
                    System.out.println("  ✓ 找到 username 字段");
                }
                if ("email".equals(field.getName())) {
                    foundEmail = true;
                    System.out.println("  ✓ 找到 email 字段");
                }
            }

            if (!foundUserId || !foundUsername || !foundEmail) {
                throw new AssertionError("应该找到 MockUserInfo 的所有字段: userId=" + foundUserId + ", username=" + foundUsername + ", email=" + foundEmail);
            }

            System.out.println("  ✓ 测试通过：抽象类和接口继承场景处理成功\n");
            passed++;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试5：通配符泛型测试
        try {
            System.out.println("【测试5】通配符泛型测试");
            ApidocDevtools devtools = GenericTypeTest.createApidocDevtoolsInstance();

            GetFieldExcled request = GetFieldExcled.builder()
                    .controllerClassName(GenericTypeTest.MockWildcardTestController.class.getName())
                    .methodName("wildcardTest")
                    .parameterIndex(0)
                    .excledFields(java.util.Collections.emptySet())
                    .build();

            List<Field> fields = devtools.getFieldsFromMethod(request);

            if (fields == null || fields.isEmpty()) {
                throw new AssertionError("字段列表不能为空");
            }

            // 验证通配符字段 usersWithExtends 应该有子字段
            boolean foundUsersWithExtends = false, hasChildren = false;
            for (Field field : fields) {
                if ("usersWithExtends".equals(field.getName())) {
                    foundUsersWithExtends = true;
                    if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                        hasChildren = true;
                        System.out.println("  ✓ 找到 usersWithExtends 字段并成功展开");
                    }
                    break;
                }
            }

            if (!foundUsersWithExtends || !hasChildren) {
                throw new AssertionError("usersWithExtends 字段应该存在并包含子字段");
            }

            System.out.println("  ✓ 测试通过：通配符泛型处理成功\n");
            passed++;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 总结
        System.out.println("========================================");
        System.out.println("测试总结：");
        System.out.println("  通过: " + passed);
        System.out.println("  失败: " + failed);
        System.out.println("  总计: " + (passed + failed));
        System.out.println("========================================");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.out.println("\n✓ 所有测试通过！");
        }
    }
}
