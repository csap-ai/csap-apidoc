package ai.csap.apidoc.devtools.core;

import java.util.Collections;
import java.util.List;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.devtools.model.GetFieldExcled;
import ai.csap.apidoc.devtools.model.api.Field;

/**
 * 测试继承泛型父类场景的字段获取
 *
 * 模拟用户场景：BaseServCatalogRequest 继承泛型父类的情况
 *
 * @author ycf
 */
public class InheritedGenericTypeTest {

    // ============ 模拟类定义 ============

    /**
     * 模拟的实体类 - 类似于用户的 CatalogEntity
     */
    @ApiModel(description = "目录实体")
    public static class CatalogEntity {
        @ApiModelProperty(value = "目录ID")
        private Long catalogId;

        @ApiModelProperty(value = "目录名称")
        private String catalogName;

        @ApiModelProperty(value = "父目录ID")
        private Long parentId;

        @ApiModelProperty(value = "排序")
        private Integer sortOrder;
    }

    /**
     * 模拟的泛型父类 - 类似于用户的 BaseRequest<T>
     * 父类包含泛型字段
     */
    @ApiModel(description = "基础请求")
    public static class BaseServRequest<T> {
        @ApiModelProperty(value = "泛型数据")
        private T data;

        @ApiModelProperty(value = "泛型列表数据")
        private List<T> dataList;

        @ApiModelProperty(value = "请求ID")
        private String requestId;

        @ApiModelProperty(value = "时间戳")
        private Long timestamp;
    }

    /**
     * 模拟的子类请求 - 类似于用户的 BaseServCatalogRequest
     * 继承泛型父类并指定具体的泛型类型
     */
    @ApiModel(description = "目录请求")
    public static class BaseServCatalogRequest extends BaseServRequest<CatalogEntity> {
        @ApiModelProperty(value = "额外参数")
        private String extraParam;

        @ApiModelProperty(value = "是否包含子目录")
        private Boolean includeChildren;
    }

    /**
     * 模拟的多层继承场景
     * 父类的父类也有泛型
     */
    @ApiModel(description = "分页请求基类")
    public static class PageRequest<T> extends BaseServRequest<T> {
        @ApiModelProperty(value = "页码")
        private Integer pageNum;

        @ApiModelProperty(value = "每页大小")
        private Integer pageSize;
    }

    /**
     * 继承分页请求的具体类
     */
    @ApiModel(description = "目录分页请求")
    public static class CatalogPageRequest extends PageRequest<CatalogEntity> {
        @ApiModelProperty(value = "搜索关键词")
        private String keyword;
    }

    /**
     * 模拟的 Controller
     */
    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/api/catalog")
    public static class MockCatalogController {

        @org.springframework.web.bind.annotation.PostMapping("/list")
        public String queryList(@org.springframework.web.bind.annotation.RequestBody BaseServCatalogRequest request) {
            return "ok";
        }

        @org.springframework.web.bind.annotation.PostMapping("/page")
        public String queryPage(@org.springframework.web.bind.annotation.RequestBody CatalogPageRequest request) {
            return "ok";
        }
    }

    // ============ 测试方法 ============

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("测试：继承泛型父类的字段获取");
        System.out.println("========================================\n");

        int passed = 0;
        int failed = 0;

        // 测试1：单层继承
        try {
            System.out.println("【测试1】单层继承：BaseServCatalogRequest extends BaseServRequest<CatalogEntity>");
            passed += testSingleInheritance() ? 1 : 0;
        } catch (Exception e) {
            System.out.println("  ✗ 测试失败: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // 测试2：多层继承
        try {
            System.out.println("\n【测试2】多层继承：CatalogPageRequest extends PageRequest<CatalogEntity> extends BaseServRequest<T>");
            passed += testMultiLevelInheritance() ? 1 : 0;
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
     * 测试单层继承场景
     */
    private static boolean testSingleInheritance() throws Exception {
        ApidocDevtools devtools = GenericTypeTest.createApidocDevtoolsInstance();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(MockCatalogController.class.getName())
                .methodName("queryList")
                .parameterIndex(0)
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        System.out.println("  字段列表:");
        printFieldList(fields, "    ");

        // 验证
        boolean hasData = false, hasDataList = false, hasRequestId = false, hasExtraParam = false;
        boolean dataHasChildren = false, dataListHasChildren = false;

        for (Field field : fields) {
            String name = field.getName();
            System.out.println("  检查字段: " + name + ", 类型: " + field.getDataType() + ", 完整类型: " + field.getFinalClassName());

            if ("data".equals(name)) {
                hasData = true;
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    dataHasChildren = true;
                    System.out.println("    ✓ data 字段有子字段，数量: " + field.getChildrenField().size());
                    // 验证子字段是否是 CatalogEntity 的字段
                    @SuppressWarnings("unchecked")
                    List<Field> children = (List<Field>) field.getChildrenField();
                    for (Field child : children) {
                        System.out.println("      - " + child.getName() + ": " + child.getDataType());
                    }
                } else {
                    System.out.println("    ✗ data 字段没有子字段！");
                }
            }
            if ("dataList".equals(name)) {
                hasDataList = true;
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    dataListHasChildren = true;
                    System.out.println("    ✓ dataList 字段有子字段，数量: " + field.getChildrenField().size());
                } else {
                    System.out.println("    ✗ dataList 字段没有子字段！");
                }
            }
            if ("requestId".equals(name)) hasRequestId = true;
            if ("extraParam".equals(name)) hasExtraParam = true;
        }

        // 验证结果
        boolean success = true;
        if (!hasData) {
            System.out.println("  ✗ 缺少继承的 data 字段");
            success = false;
        }
        if (!hasDataList) {
            System.out.println("  ✗ 缺少继承的 dataList 字段");
            success = false;
        }
        if (!hasRequestId) {
            System.out.println("  ✗ 缺少继承的 requestId 字段");
            success = false;
        }
        if (!hasExtraParam) {
            System.out.println("  ✗ 缺少子类的 extraParam 字段");
            success = false;
        }
        if (!dataHasChildren) {
            System.out.println("  ✗ data 字段的泛型类型没有正确解析为 CatalogEntity");
            success = false;
        }
        if (!dataListHasChildren) {
            System.out.println("  ✗ dataList 字段的泛型类型没有正确解析为 List<CatalogEntity>");
            success = false;
        }

        if (success) {
            System.out.println("  ✓ 测试通过：单层继承场景处理正确！");
        }
        return success;
    }

    /**
     * 测试多层继承场景
     */
    private static boolean testMultiLevelInheritance() throws Exception {
        ApidocDevtools devtools = GenericTypeTest.createApidocDevtoolsInstance();

        GetFieldExcled request = GetFieldExcled.builder()
                .controllerClassName(MockCatalogController.class.getName())
                .methodName("queryPage")
                .parameterIndex(0)
                .excledFields(Collections.emptySet())
                .build();

        List<Field> fields = devtools.getFieldsFromMethod(request);

        System.out.println("  获取到的字段数量: " + fields.size());
        System.out.println("  字段列表:");
        printFieldList(fields, "    ");

        // 验证
        boolean hasData = false, hasPageNum = false, hasKeyword = false;
        boolean dataHasChildren = false;

        for (Field field : fields) {
            String name = field.getName();

            if ("data".equals(name)) {
                hasData = true;
                if (field.getChildrenField() != null && !field.getChildrenField().isEmpty()) {
                    dataHasChildren = true;
                    System.out.println("    ✓ data 字段有子字段（来自祖父类 BaseServRequest）");
                }
            }
            if ("pageNum".equals(name)) {
                hasPageNum = true;
                System.out.println("    ✓ 找到 pageNum 字段（来自父类 PageRequest）");
            }
            if ("keyword".equals(name)) {
                hasKeyword = true;
                System.out.println("    ✓ 找到 keyword 字段（来自当前类）");
            }
        }

        boolean success = true;
        if (!hasData) {
            System.out.println("  ✗ 缺少来自祖父类的 data 字段");
            success = false;
        }
        if (!hasPageNum) {
            System.out.println("  ✗ 缺少来自父类的 pageNum 字段");
            success = false;
        }
        if (!hasKeyword) {
            System.out.println("  ✗ 缺少当前类的 keyword 字段");
            success = false;
        }
        if (!dataHasChildren) {
            System.out.println("  ✗ data 字段的泛型类型没有正确解析为 CatalogEntity");
            success = false;
        }

        if (success) {
            System.out.println("  ✓ 测试通过：多层继承场景处理正确！");
        }
        return success;
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

