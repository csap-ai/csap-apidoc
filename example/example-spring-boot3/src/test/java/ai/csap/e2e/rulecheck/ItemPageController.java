package ai.csap.e2e.rulecheck;

import java.util.Arrays;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;

/**
 * 原来的 listItems(返回 Result&lt;Item&gt;)改名为 pageItems 并改成分页返回,YAML 规则没跟着改
 */
@RestController
@RequestMapping("/e2e/rule-check/items")
@Api(value = "条目(规则过期检查)")
public class ItemPageController {

    @ApiOperation("分页查询")
    @GetMapping("/page")
    public Result<Page<Item>> pageItems(@RequestParam(required = false) String keyword) {
        return new Result<>(new Page<>(2, Arrays.asList(new Item(1L, "a"), new Item(2L, "b"))));
    }

    @ApiOperation("详情")
    @GetMapping("/detail")
    public Result<Item> detail(@RequestParam Long id) {
        return new Result<>(new Item(id, "x"));
    }
}
