package ai.csap.e2e.filter;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;

@RestController
@RequestMapping("/e2e/orders")
@Api(value = "订单(返回字段过滤测试)")
public class OrderController {

    @ApiOperation("详情")
    @GetMapping("/detail")
    public Order detail() {
        return new Order();
    }

    @ApiOperation("列表")
    @GetMapping("/list")
    public List<Order> list() {
        return List.of(new Order(), new Order());
    }

    @ApiOperation("未配置规则")
    @GetMapping("/plain")
    public Order plain() {
        return new Order();
    }
}
