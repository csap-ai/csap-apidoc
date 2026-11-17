package ai.csap.validation.boot.autoconfigure.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.ApiOperation;

@RestController
public class DemoController {

    @ApiOperation("用户列表")
    @GetMapping("/users")
    public String list(@RequestParam(required = false) String id) {
        return "ok";
    }

    @ApiOperation("创建用户")
    @PostMapping("/users")
    public String create(@RequestBody DemoUser user) {
        return "ok";
    }

    @GetMapping("/plain")
    public String plain(@RequestParam(required = false) String id) {
        return "ok";
    }
}
