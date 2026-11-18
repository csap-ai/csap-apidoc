package ai.csap.apidoc.boot.autoconfigure.dbe2e;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/save")
    @ApiOperation(value = "保存", description = "保存", status = ai.csap.apidoc.annotation.ApiStatus.FINISH)
    public DbE2eResp save(@RequestBody DbE2eReq req) {
        DbE2eResp resp = new DbE2eResp();
        resp.setName(req.getName());
        return resp;
    }
}
