package ai.csap.apidoc.boot.autoconfigure.dbe2e;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(value = "dbe2e", description = "数据库模式端到端")
@RequestMapping("/dbe2e")
public class DbE2eController {

    @GetMapping("/query")
    @ApiOperation(value = "查询", description = "查询")
    public String query(@RequestParam String keyword) {
        return keyword;
    }
}
