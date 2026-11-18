package ai.csap.example.controller;

import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.example.model.Response;
import ai.csap.example.model.User;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced API Documentation Examples
 *
 * 演示 @ApiProperty 的用法:直接标注在方法参数上。
 * 标注在方法上(以及 @ApiPropertys)的写法已弃用,文档生成不会读取。
 *
 * @author CSAP Team
 */
@RestController
@RequestMapping("/api/advanced")
@Api(value = "高级示例", description = "展示 @ApiProperty 标注在参数上的用法")
public class AdvancedController {

    /**
     * Example 1: 单个查询参数
     */
    @GetMapping("/search")
    @ApiOperation(value = "搜索用户", description = "根据关键词搜索用户")
    public Response<List<User>> searchByKeyword(
            @ApiProperty(value = "搜索关键词", description = "用户名或邮箱关键词",
                    required = true, example = "john", paramType = ParamType.QUERY)
            String keyword) {
        List<User> users = new ArrayList<>();
        return Response.success(users);
    }

    /**
     * Example 2: 多个查询参数,每个参数各自标注
     */
    @GetMapping("/filter")
    @ApiOperation(value = "过滤用户", description = "根据多个条件过滤用户")
    public Response<List<User>> filterUsers(
            @ApiProperty(value = "最小年龄", description = "最小年龄限制", example = "18",
                    paramType = ParamType.QUERY) Integer minAge,
            @ApiProperty(value = "最大年龄", description = "最大年龄限制", example = "60",
                    paramType = ParamType.QUERY) Integer maxAge,
            @ApiProperty(value = "城市", description = "所在城市", example = "北京",
                    paramType = ParamType.QUERY) String city) {
        List<User> users = new ArrayList<>();
        return Response.success(users);
    }

    /**
     * Example 3: 与 @RequestParam 一起使用
     */
    @GetMapping("/page")
    @ApiOperation(value = "分页查询", description = "分页查询用户列表")
    public Response<List<User>> pageQuery(
            @RequestParam(defaultValue = "1")
            @ApiProperty(value = "页码", description = "当前页码，从1开始", example = "1",
                    defaultValue = "1", paramType = ParamType.QUERY) Integer page,
            @RequestParam(defaultValue = "10")
            @ApiProperty(value = "每页大小", description = "每页显示的记录数", example = "10",
                    defaultValue = "10", paramType = ParamType.QUERY) Integer size) {
        List<User> users = new ArrayList<>();
        return Response.success(users);
    }

    /**
     * Example 4: 与 @PathVariable 一起使用
     */
    @GetMapping("/user/{userId}")
    @ApiOperation(value = "获取指定用户", description = "根据用户ID获取用户信息")
    public Response<User> getUserById(
            @PathVariable
            @ApiProperty(value = "用户ID", description = "要查询的用户ID", required = true,
                    example = "1001", paramType = ParamType.PATH) Long userId) {
        User user = new User();
        user.setId(userId);
        return Response.success(user);
    }
}
