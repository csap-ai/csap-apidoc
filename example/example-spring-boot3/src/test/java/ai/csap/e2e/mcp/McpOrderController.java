package ai.csap.e2e.mcp;

import java.util.Collections;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;

@RestController
@RequestMapping("/e2e/mcp/orders")
@Api(value = "订单(MCP 测试)")
public class McpOrderController {

    @ApiOperation(value = "订单详情", description = "按 ID 查询订单")
    @GetMapping("/{id}")
    public McpOrder get(@PathVariable("id") Long id, @RequestHeader(value = "X-Tenant", required = false) String tenant,
                        @RequestParam(required = false) String lang) {
        McpOrder o = new McpOrder();
        o.setId(id);
        o.setNo("NO-" + id);
        o.setTenant(tenant);
        o.setLang(lang);
        o.setPassword("should-be-filtered");
        return o;
    }

    @ApiOperation("调用方信息")
    @GetMapping("/whoami")
    public Map<String, String> whoami(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return Collections.singletonMap("authorization", authorization == null ? "none" : authorization);
    }

    @ApiOperation("新建订单")
    @PostMapping
    public McpOrder create(@RequestBody McpOrder order) {
        order.setId(100L);
        return order;
    }

    @ApiOperation("删除订单")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        return Collections.singletonMap("deleted", id);
    }
}
