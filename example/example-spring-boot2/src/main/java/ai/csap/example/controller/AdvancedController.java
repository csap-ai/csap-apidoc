package ai.csap.example.controller;

import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.annotation.ApiPropertys;
import ai.csap.example.model.Response;
import ai.csap.example.model.User;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced API Documentation Examples
 *
 * 演示 @ApiProperty 和 @ApiPropertys 的正确用法
 *
 * @author CSAP Team
 */
@RestController
@RequestMapping("/api/advanced")
@Api(value = "高级示例", description = "展示 @ApiProperty 和 @ApiPropertys 的正确用法")
public class AdvancedController {

    /**
     * Example 1: Using @ApiProperty for single parameter
     *
     * 方法有一个参数，用 @ApiProperty 补充文档说明
     */
    @GetMapping("/search")
    @ApiOperation(value = "搜索用户", description = "根据关键词搜索用户")
    @ApiProperty(
        value = "搜索关键词",
        name = "keyword",              // 对应方法参数名 keyword
        description = "用户名或邮箱关键词",
        required = true,
        example = "john",
        paramType = ParamType.QUERY
    )
    public Response<List<User>> searchByKeyword(String keyword) {
        // 方法有实际参数 keyword
        // @ApiProperty 通过 name="keyword" 来匹配这个参数
        List<User> users = new ArrayList<>();
        return Response.success(users);
    }

    /**
     * Example 2: Using @ApiPropertys for multiple parameters
     *
     * 方法有多个参数，用 @ApiPropertys 定义所有参数的文档
     */
    @GetMapping("/filter")
    @ApiOperation(value = "过滤用户", description = "根据多个条件过滤用户")
    @ApiPropertys({
        @ApiProperty(
            value = "最小年龄",
            name = "minAge",          // 对应参数 minAge
            description = "最小年龄限制",
            required = false,
            example = "18",
            paramType = ParamType.QUERY,
            dataTypeClass = Integer.class
        ),
        @ApiProperty(
            value = "最大年龄",
            name = "maxAge",          // 对应参数 maxAge
            description = "最大年龄限制",
            required = false,
            example = "60",
            paramType = ParamType.QUERY,
            dataTypeClass = Integer.class
        ),
        @ApiProperty(
            value = "城市",
            name = "city",            // 对应参数 city
            description = "所在城市",
            required = false,
            example = "北京",
            paramType = ParamType.QUERY,
            dataTypeClass = String.class
        )
    })
    public Response<List<User>> filterUsers(Integer minAge, Integer maxAge, String city) {
        // 方法有3个实际参数：minAge, maxAge, city
        // @ApiPropertys 通过 name 属性分别匹配这3个参数
        List<User> users = new ArrayList<>();
        return Response.success(users);
    }

    /**
     * Example 3: Mixing @RequestParam with @ApiProperty
     *
     * 结合 @RequestParam 使用 @ApiProperty
     */
    @GetMapping("/page")
    @ApiOperation(value = "分页查询", description = "分页查询用户列表")
    @ApiPropertys({
        @ApiProperty(
            value = "页码",
            name = "page",           // 对应参数 page
            description = "当前页码，从1开始",
            required = false,
            example = "1",
            defaultValue = "1",
            paramType = ParamType.QUERY,
            dataTypeClass = Integer.class
        ),
        @ApiProperty(
            value = "每页大小",
            name = "size",          // 对应参数 size
            description = "每页显示的记录数",
            required = false,
            example = "10",
            defaultValue = "10",
            paramType = ParamType.QUERY,
            dataTypeClass = Integer.class
        )
    })
    public Response<List<User>> pageQuery(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        // 方法有实际参数 page 和 size（带 @RequestParam）
        // @ApiPropertys 提供了额外的文档说明
        List<User> users = new ArrayList<>();
        return Response.success(users);
    }

    /**
     * Example 4: Using @ApiProperty with @PathVariable
     *
     * 结合 @PathVariable 使用 @ApiProperty
     */
    @GetMapping("/user/{userId}")
    @ApiOperation(value = "获取指定用户", description = "根据用户ID获取用户信息")
    @ApiProperty(
        value = "用户ID",
        name = "userId",           // 对应 @PathVariable 的 userId
        description = "要查询的用户ID",
        required = true,
        example = "1001",
        paramType = ParamType.PATH,
        dataTypeClass = Long.class
    )
    public Response<User> getUserById(@PathVariable Long userId) {
        // 方法有实际参数 userId
        // @ApiProperty 补充了这个参数的文档说明
        User user = new User();
        user.setId(userId);
        return Response.success(user);
    }
}

