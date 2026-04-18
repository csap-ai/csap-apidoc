package ai.csap.apidoc.devtools.core;

import java.util.List;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.devtools.model.GetFieldExcled;
import ai.csap.apidoc.devtools.model.api.Field;

/**
 * 泛型类型获取测试
 *
 * 测试 getFieldsFromMethod 方法，验证：
 * 1. 从方法参数中获取泛型类型并解析字段
 * 2. 从方法返回值中获取泛型类型并解析字段
 *
 * 本测试类包含了模拟的 Controller 和方法，用于测试泛型类型的字段获取
 *
 * @author ycf
 * @date 2025/01/XX
 */
public class GenericTypeTest {

    /**
     * 模拟的 Controller - 复制自 ComplexTestController
     */
    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/api/complex")
    public static class MockComplexTestController {

        /**
         * 场景5：泛型参数和返回值
         * 复制自 ComplexTestController.genericTest
         */
        @org.springframework.web.bind.annotation.PostMapping("/generic")
        public MockApiResult<MockGenericResponse<MockUserInfo>> genericTest(@org.springframework.web.bind.annotation.RequestBody MockGenericRequest<MockUserInfo> request) {
            return null;
        }
    }

    /**
     * 模拟的 ApiResult - 用于包装返回值
     */
    @ApiModel(description = "API结果包装")
    public static class MockApiResult<T> {
        @ApiModelProperty(value = "数据")
        private T data;

        @ApiModelProperty(value = "是否成功")
        private Boolean success;

        @ApiModelProperty(value = "消息")
        private String message;
    }

    /**
     * 模拟的泛型响应类 - 复制自 ComplexTestResponses.GenericResponse
     */
    @ApiModel(description = "泛型参数响应")
    public static class MockGenericResponse<T> {
        @ApiModelProperty(value = "泛型数据")
        private T data;

        @ApiModelProperty(value = "响应元数据")
        private MockResponseMeta meta;
    }

    /**
     * 模拟的响应元数据类
     */
    @ApiModel(description = "响应元数据")
    public static class MockResponseMeta {
        @ApiModelProperty(value = "响应ID")
        private String responseId;
    }

    /**
     * 模拟的泛型请求类 - 复制自 ComplexTestRequests.GenericRequest
     */
    @ApiModel(description = "泛型参数请求")
    public static class MockGenericRequest<T> {
        @ApiModelProperty(value = "泛型数据")
        private T data;

        @ApiModelProperty(value = "泛型列表")
        private List<T> dataList;

        @ApiModelProperty(value = "请求元数据")
        private MockRequestMeta meta;
    }

    /**
     * 模拟的用户信息类 - 复制自 ComplexTestModels.UserInfo
     */
    @ApiModel(description = "用户信息")
    public static class MockUserInfo {
        @ApiModelProperty(value = "用户ID")
        private Long userId;

        @ApiModelProperty(value = "用户名")
        private String username;

        @ApiModelProperty(value = "邮箱")
        private String email;
    }

    /**
     * 模拟的请求元数据类
     */
    @ApiModel(description = "请求元数据")
    public static class MockRequestMeta {
        @ApiModelProperty(value = "请求ID")
        private String requestId;
    }

    /**
     * 测试继承场景的 Controller
     */
    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/api/inheritance")
    public static class MockInheritanceTestController {

        /**
         * 场景：继承泛型父类
         * 父类有泛型参数，子类继承并指定了具体的泛型类型
         */
        @org.springframework.web.bind.annotation.PostMapping("/inherited")
        public MockApiResult<MockInheritedUserResponse> inheritedTest(@org.springframework.web.bind.annotation.RequestBody MockInheritedUserRequest request) {
            return null;
        }
    }

    /**
     * 泛型基类请求
     */
    @ApiModel(description = "泛型基类请求")
    public static class MockBaseRequest<T> {
        @ApiModelProperty(value = "泛型数据")
        private T data;

        @ApiModelProperty(value = "请求ID")
        private String requestId;
    }

    /**
     * 继承泛型父类的子类请求
     * 继承 BaseRequest<UserInfo>，指定了泛型参数为 MockUserInfo
     */
    @ApiModel(description = "继承的用户请求")
    public static class MockInheritedUserRequest extends MockBaseRequest<MockUserInfo> {
        @ApiModelProperty(value = "额外信息")
        private String extraInfo;
    }

    /**
     * 泛型基类响应
     */
    @ApiModel(description = "泛型基类响应")
    public static class MockBaseResponse<T> {
        @ApiModelProperty(value = "泛型数据")
        private T data;

        @ApiModelProperty(value = "状态码")
        private Integer code;
    }

    /**
     * 继承泛型父类的子类响应
     */
    @ApiModel(description = "继承的用户响应")
    public static class MockInheritedUserResponse extends MockBaseResponse<MockUserInfo> {
        @ApiModelProperty(value = "额外信息")
        private String extraMessage;
    }

    /**
     * 测试抽象类和接口继承场景的 Controller 接口
     * 模拟 CURD 场景的通用接口
     */
    public interface MockBaseControllerInterface<T, ID> {
        @org.springframework.web.bind.annotation.PostMapping("/create")
        MockApiResult<T> create(@org.springframework.web.bind.annotation.RequestBody T entity);

        @org.springframework.web.bind.annotation.GetMapping("/{id}")
        MockApiResult<T> getById(@org.springframework.web.bind.annotation.PathVariable ID id);

        @org.springframework.web.bind.annotation.PutMapping("/update")
        MockApiResult<T> update(@org.springframework.web.bind.annotation.RequestBody T entity);

        @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
        MockApiResult<Boolean> delete(@org.springframework.web.bind.annotation.PathVariable ID id);
    }

    /**
     * 抽象基类 Controller
     * 实现了接口，提供通用的 CURD 方法
     */
    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/api/base")
    public abstract static class MockAbstractBaseController<T, ID> implements MockBaseControllerInterface<T, ID> {

        // 这些方法在抽象类中实现，使用泛型类型
        @Override
        public MockApiResult<T> create(@org.springframework.web.bind.annotation.RequestBody T entity) {
            return null; // 实现逻辑
        }

        @Override
        public MockApiResult<T> getById(@org.springframework.web.bind.annotation.PathVariable ID id) {
            return null; // 实现逻辑
        }

        @Override
        public MockApiResult<T> update(@org.springframework.web.bind.annotation.RequestBody T entity) {
            return null; // 实现逻辑
        }

        @Override
        public MockApiResult<Boolean> delete(@org.springframework.web.bind.annotation.PathVariable ID id) {
            return null; // 实现逻辑
        }
    }

    /**
     * 具体的子 Controller
     * 继承抽象基类并指定泛型类型，但没有重写方法（使用父类的方法）
     */
    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/api/user")
    public static class MockUserController extends MockAbstractBaseController<MockUserInfo, Long> {
        // 没有重写父类的方法，使用继承的方法
        // 父类方法中的 T 应该是 MockUserInfo，ID 应该是 Long
    }

    /**
     * 测试通配符泛型的 Controller
     */
    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/api/wildcard")
    public static class MockWildcardTestController {

        /**
         * 场景：通配符泛型参数
         */
        @org.springframework.web.bind.annotation.PostMapping("/wildcard")
        public MockApiResult<Boolean> wildcardTest(@org.springframework.web.bind.annotation.RequestBody MockWildcardRequest request) {
            return null;
        }
    }

    /**
     * 包含通配符泛型的请求类
     */
    @ApiModel(description = "通配符泛型请求")
    public static class MockWildcardRequest {
        @ApiModelProperty(value = "上界通配符列表")
        private java.util.List<? extends MockUserInfo> usersWithExtends;

        @ApiModelProperty(value = "下界通配符列表")
        private java.util.List<? super MockUserInfo> usersWithSuper;

        @ApiModelProperty(value = "无界通配符列表")
        private java.util.List<?> wildcardList;

        @ApiModelProperty(value = "普通字段")
        private String name;
    }

    /**
     * 测试：直接调用 getFieldsFromMethod 方法获取参数泛型类型
     */
    @Test
    public void testCallGetFieldsFromMethodForParameter() throws Exception {
        System.out.println("=== 测试：直接调用 getFieldsFromMethod 获取参数泛型类型 ===");

        // 创建请求对象
        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(MockComplexTestController.class.getName())
                .methodName("genericTest")
                .parameterIndex(0)  // 第一个参数
                .excledFields(java.util.Collections.emptySet())
                .build();

        System.out.println("请求参数:");
        System.out.println("  controllerClassName: " + request.getControllerClassName());
        System.out.println("  methodName: " + request.getMethodName());
        System.out.println("  parameterIndex: " + request.getParameterIndex());

        // 创建 ApidocDevtools 实例（使用 Mock 依赖）
        ApidocDevtools devtoolsService = createApidocDevtoolsInstance();

        // 直接调用 getFieldsFromMethod
        List<Field> fields = devtoolsService.getFieldsFromMethod(request);

        System.out.println("\n✓ 成功获取字段信息，字段数量: " + fields.size());
        System.out.println("\n字段列表:");
        printFieldList(fields, "");

        // 验证结果
        assert fields != null : "字段列表不能为null";
        assert !fields.isEmpty() : "应该至少有一个字段";
        System.out.println("\n✓ 测试通过！");
    }

    /**
     * 测试：直接调用 getFieldsFromMethod 方法获取返回值泛型类型
     */
    @Test
    public void testCallGetFieldsFromMethodForReturnType() throws Exception {
        System.out.println("=== 测试：直接调用 getFieldsFromMethod 获取返回值泛型类型 ===");

        // 创建请求对象（parameterIndex = -1 表示返回值）
        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(MockComplexTestController.class.getName())
                .methodName("genericTest")
                .parameterIndex(-1)  // -1 表示返回值
                .excledFields(java.util.Collections.emptySet())
                .build();

        System.out.println("请求参数:");
        System.out.println("  controllerClassName: " + request.getControllerClassName());
        System.out.println("  methodName: " + request.getMethodName());
        System.out.println("  parameterIndex: " + request.getParameterIndex() + " (负数表示返回值)");

        // 创建 ApidocDevtools 实例（使用 Mock 依赖）
        ApidocDevtools devtoolsService = createApidocDevtoolsInstance();

        // 直接调用 getFieldsFromMethod
        List<Field> fields = devtoolsService.getFieldsFromMethod(request);

        System.out.println("\n✓ 成功获取返回值字段信息，字段数量: " + fields.size());
        System.out.println("\n返回值字段列表:");
        printFieldList(fields, "");

        // 验证结果
        assert fields != null : "字段列表不能为null";
        assert !fields.isEmpty() : "应该至少有一个字段";
        System.out.println("\n✓ 测试通过！");
    }

    /**
     * 创建 ApidocDevtools 实例用于测试
     * 创建简单的 Mock 对象来避免 NullPointerException
     */
    public static ApidocDevtools createApidocDevtoolsInstance() throws Exception {
        try {
            // 获取构造函数
            java.lang.reflect.Constructor<ApidocDevtools> constructor =
                ApidocDevtools.class.getDeclaredConstructor(
                    ai.csap.apidoc.autoconfigure.EnableApidocConfig.class,
                    org.springframework.context.ApplicationContext.class,
                    ai.csap.apidoc.properties.CsapDocConfig.class
                );

            constructor.setAccessible(true);

            // 创建简单的 Mock 对象（使用代理或者直接传入 null，如果构造函数允许的话）
            // 先尝试传入 null，如果不允许会抛出异常
            try {
                return constructor.newInstance(null, null, null);
            } catch (Exception e) {
                // 如果传入 null 失败，创建最简单的实现
                // 使用反射创建空对象
                ai.csap.apidoc.autoconfigure.EnableApidocConfig mockConfig =
                    (ai.csap.apidoc.autoconfigure.EnableApidocConfig)
                    java.lang.reflect.Proxy.newProxyInstance(
                        ai.csap.apidoc.autoconfigure.EnableApidocConfig.class.getClassLoader(),
                        new Class[]{ai.csap.apidoc.autoconfigure.EnableApidocConfig.class},
                        (proxy, method, args) -> null
                    );

                org.springframework.context.ApplicationContext mockContext =
                    (org.springframework.context.ApplicationContext)
                    java.lang.reflect.Proxy.newProxyInstance(
                        org.springframework.context.ApplicationContext.class.getClassLoader(),
                        new Class[]{org.springframework.context.ApplicationContext.class},
                        (proxy, method, args) -> null
                    );

                ai.csap.apidoc.properties.CsapDocConfig mockDocConfig =
                    (ai.csap.apidoc.properties.CsapDocConfig)
                    java.lang.reflect.Proxy.newProxyInstance(
                        ai.csap.apidoc.properties.CsapDocConfig.class.getClassLoader(),
                        new Class[]{ai.csap.apidoc.properties.CsapDocConfig.class},
                        (proxy, method, args) -> null
                    );

                return constructor.newInstance(mockConfig, mockContext, mockDocConfig);
            }

        } catch (Exception e) {
            throw new RuntimeException("无法创建 ApidocDevtools 实例: " + e.getMessage(), e);
        }
    }

    /**
     * 打印字段列表
     */
    @SuppressWarnings("unchecked")
    private void printFieldList(List<Field> fields, String prefix) {
        if (fields == null || fields.isEmpty()) {
            System.out.println(prefix + "  (无字段)");
            return;
        }

        for (Field field : fields) {
            System.out.println(prefix + "字段: " + field.getName());
            System.out.println(prefix + "  类型: " + field.getDataType());
            System.out.println(prefix + "  完整类型: " + field.getFinalClassName());
            System.out.println(prefix + "  描述: " + field.getValue());

            if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                List<Field> children = (List<Field>) field.getChildrenField();
                System.out.println(prefix + "  子字段 (" + children.size() + " 个):");
                printFieldList(children, prefix + "    ");
            }
        }
    }

    /**
     * 测试：继承场景 - 父类有泛型，子类继承并指定泛型参数
     */
    @Test
    public void testCallGetFieldsFromMethodForInheritance() throws Exception {
        System.out.println("=== 测试：继承场景 - 父类有泛型，子类继承并指定泛型参数 ===");

        // 创建请求对象
        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(MockInheritanceTestController.class.getName())
                .methodName("inheritedTest")
                .parameterIndex(0)  // 第一个参数
                .excledFields(java.util.Collections.emptySet())
                .build();

        System.out.println("请求参数:");
        System.out.println("  controllerClassName: " + request.getControllerClassName());
        System.out.println("  methodName: " + request.getMethodName());
        System.out.println("  parameterIndex: " + request.getParameterIndex());

        // 创建 ApidocDevtools 实例（使用 Mock 依赖）
        ApidocDevtools devtoolsService = createApidocDevtoolsInstance();

        // 直接调用 getFieldsFromMethod
        List<Field> fields = devtoolsService.getFieldsFromMethod(request);

        System.out.println("\n✓ 成功获取字段信息，字段数量: " + fields.size());
        System.out.println("\n字段列表:");
        printFieldList(fields, "");

        // 验证结果
        assert fields != null : "字段列表不能为null";
        assert !fields.isEmpty() : "应该至少有一个字段";

        // 验证继承的字段：应该能找到父类中的 data 字段（类型应该是 MockUserInfo）
        boolean foundDataField = false;
        boolean foundInheritedUserInfo = false;
        for (Field field : fields) {
            if ("data".equals(field.getName())) {
                foundDataField = true;
                System.out.println("\n✓ 找到继承的 data 字段");
                System.out.println("  类型: " + field.getDataType());
                System.out.println("  完整类型: " + field.getFinalClassName());

                // 检查 data 字段是否有子字段（应该是 MockUserInfo 的字段）
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    foundInheritedUserInfo = true;
                    System.out.println("✓ data 字段成功解析为 MockUserInfo，包含子字段");
                }
                break;
            }
        }

        assert foundDataField : "应该找到继承的 data 字段";
        assert foundInheritedUserInfo : "data 字段应该被解析为 MockUserInfo 并包含子字段";

        System.out.println("\n✓ 测试通过！");
    }

    /**
     * 测试：抽象类和接口继承场景 - CURD 场景
     * 父类有泛型方法，子类继承但没有重写
     */
    @Test
    public void testCallGetFieldsFromMethodForAbstractInheritance() throws Exception {
        System.out.println("=== 测试：抽象类和接口继承场景（CURD场景）===");

        // 测试 create 方法的参数（从抽象父类继承的方法）
        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(MockUserController.class.getName())
                .methodName("create")
                .parameterIndex(0)  // 第一个参数 entity
                .excledFields(java.util.Collections.emptySet())
                .build();

        System.out.println("请求参数:");
        System.out.println("  controllerClassName: " + request.getControllerClassName());
        System.out.println("  methodName: " + request.getMethodName() + " (从抽象父类继承)");
        System.out.println("  parameterIndex: " + request.getParameterIndex());

        // 创建 ApidocDevtools 实例
        ApidocDevtools devtoolsService = createApidocDevtoolsInstance();

        // 直接调用 getFieldsFromMethod
        List<Field> fields = devtoolsService.getFieldsFromMethod(request);

        System.out.println("\n✓ 成功获取字段信息，字段数量: " + fields.size());
        System.out.println("\n字段列表:");
        printFieldList(fields, "");

        // 验证结果
        assert fields != null : "字段列表不能为null";
        assert !fields.isEmpty() : "应该至少有一个字段";

        // 验证：entity 参数的类型应该是 MockUserInfo，应该包含 userId、username、email 字段
        boolean foundUserId = false, foundUsername = false, foundEmail = false;
        for (Field field : fields) {
            if ("userId".equals(field.getName())) {
                foundUserId = true;
                System.out.println("\n✓ 找到 userId 字段，类型: " + field.getDataType());
            }
            if ("username".equals(field.getName())) {
                foundUsername = true;
                System.out.println("✓ 找到 username 字段，类型: " + field.getDataType());
            }
            if ("email".equals(field.getName())) {
                foundEmail = true;
                System.out.println("✓ 找到 email 字段，类型: " + field.getDataType());
            }
        }

        assert foundUserId && foundUsername && foundEmail :
            "应该找到 MockUserInfo 的所有字段: userId=" + foundUserId + ", username=" + foundUsername + ", email=" + foundEmail;

        System.out.println("\n✓ 测试通过：抽象类和接口继承场景处理成功！");
    }

    /**
     * 测试：通配符泛型场景
     */
    @Test
    public void testCallGetFieldsFromMethodForWildcard() throws Exception {
        System.out.println("=== 测试：通配符泛型场景 ===");

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(MockWildcardTestController.class.getName())
                .methodName("wildcardTest")
                .parameterIndex(0)
                .excledFields(java.util.Collections.emptySet())
                .build();

        System.out.println("请求参数:");
        System.out.println("  controllerClassName: " + request.getControllerClassName());
        System.out.println("  methodName: " + request.getMethodName());
        System.out.println("  parameterIndex: " + request.getParameterIndex());

        ApidocDevtools devtoolsService = createApidocDevtoolsInstance();
        List<Field> fields = devtoolsService.getFieldsFromMethod(request);

        System.out.println("\n✓ 成功获取字段信息，字段数量: " + fields.size());
        System.out.println("\n字段列表:");
        printFieldList(fields, "");

        assert fields != null : "字段列表不能为null";
        assert !fields.isEmpty() : "应该至少有一个字段";

        // 验证通配符字段：usersWithExtends 应该有子字段
        boolean foundUsersWithExtends = false, hasChildren = false;
        for (Field field : fields) {
            if ("usersWithExtends".equals(field.getName())) {
                foundUsersWithExtends = true;
                System.out.println("\n✓ 找到 usersWithExtends 字段");
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    hasChildren = true;
                    System.out.println("✓ usersWithExtends 字段成功展开，包含子字段");
                }
                break;
            }
        }

        assert foundUsersWithExtends : "应该找到 usersWithExtends 字段";
        assert hasChildren : "usersWithExtends 字段应该包含子字段（通配符上界类型）";

        System.out.println("\n✓ 测试通过：通配符泛型处理成功！");
    }

}
