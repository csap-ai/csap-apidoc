package ai.csap.example.controller;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.example.model.BaseRequest;
import ai.csap.example.model.Product;
import ai.csap.example.model.Response;
import ai.csap.example.model.User;
import ai.csap.example.model.UserRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 泛型测试 Controller
 *
 * 用于测试 getFieldsFromMethod 对泛型场景的支持：
 * 1. 继承泛型父类的请求参数
 * 2. 泛型返回值
 * 3. 直接使用泛型类型的参数
 *
 * @author CSAP Team
 */
@RestController
@RequestMapping("/api/generic-test")
@Api(value = "泛型测试", description = "测试泛型场景的字段获取")
public class GenericTestController {

    /**
     * 场景1：继承泛型父类的请求参数
     *
     * UserRequest 继承 BaseRequest<User>
     * 期望能获取到：
     * - 父类的 data 字段（类型应为 User，有子字段）
     * - 父类的 dataList 字段（类型应为 List<User>，有子字段）
     * - 父类的 requestId, timestamp, operator 字段
     * - 子类的 remark, batch 字段
     */
    @PostMapping("/user-request")
    @ApiOperation(value = "继承泛型父类测试", description = "测试 UserRequest extends BaseRequest<User>")
    public Response<User> testInheritedGeneric(@RequestBody UserRequest request) {
        User user = request.getData();
        return Response.success(user);
    }

    /**
     * 场景2：直接使用泛型类型参数
     *
     * 直接使用 BaseRequest<Product> 作为参数
     * 期望能获取到：
     * - data 字段（类型应为 Product，有子字段）
     * - dataList 字段（类型应为 List<Product>，有子字段）
     */
    @PostMapping("/product-request")
    @ApiOperation(value = "直接泛型参数测试", description = "测试 BaseRequest<Product> 参数")
    public Response<Product> testDirectGeneric(@RequestBody BaseRequest<Product> request) {
        Product product = request.getData();
        return Response.success(product);
    }

    /**
     * 场景3：泛型返回值
     *
     * 返回值是 Response<List<User>>
     * 期望能获取到：
     * - code, message, timestamp 字段
     * - data 字段（类型应为 List<User>，有子字段）
     */
    @GetMapping("/users")
    @ApiOperation(value = "泛型返回值测试", description = "测试 Response<List<User>> 返回值")
    public Response<List<User>> testGenericReturn() {
        return Response.success(List.of());
    }

    /**
     * 场景4：多个参数，获取指定索引的参数
     */
    @PostMapping("/multi-params")
    @ApiOperation(value = "多参数测试", description = "测试多参数场景")
    public Response<User> testMultiParams(
            @RequestParam String keyword,
            @RequestBody UserRequest request,
            @RequestParam(required = false) Boolean verbose
    ) {
        return Response.success(request.getData());
    }
}

