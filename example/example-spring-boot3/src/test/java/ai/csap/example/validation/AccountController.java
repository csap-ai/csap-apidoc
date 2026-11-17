package ai.csap.example.validation;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.example.model.Response;

/**
 * 没有 @Valid:约束注解只由 CSAP 运行时校验执行
 */
@RestController
@RequestMapping("/api/accounts")
@Api(value = "账号")
public class AccountController {

    @PostMapping
    @ApiOperation(value = "创建账号")
    public Response<Account> createAccount(@RequestBody Account account) {
        return Response.success(account);
    }
}
