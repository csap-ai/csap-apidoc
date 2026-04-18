package ai.csap.apidoc.test;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.Protocols;
import ai.csap.apidoc.test.model.Home3Model;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2019年-12月-28日 18:49:00
 **/
@Api(value = "测试controller", description = "一个测试Controller", tags = {"1", "2", "3"}, protocols = Protocols.HTTP)
@RequestMapping("test")
@RestController
public class TestWeb  {
    //
//    @ApiOperation(value = "修改2")
//    @ApiProperty(value = "名称", name = "name", defaultValue = "1")
//    @PutMapping("update")
//    public boolean update2(String name) {
//        return false;
//    }
//
//    @ApiOperation(value = "修改3")
//    @ApiPropertys({
//            @ApiProperty(value = "名称", required = true, name = "name"),
//            @ApiProperty(value = "年龄", required = true, name = "age")
//    })
//    @PutMapping("update3")
//    public boolean update3(@NotNull(message = "名称不能为空")
//                           @NotEmpty(message = "名称不能为空字符") String name,
//                           @NotNull(message = "年龄不能为空")
//                           @NotEmpty(message = "年龄不能为空字符") String age) {
//        return false;
//    }
//
//    @ApiOperation(value = "查询", httpMethod = HttpMethod.GET, description = "查询")
//    @PostMapping("authorization")
//    public ResultParam<List<TestModel<TestBean>>> authorization(
//            @RequestBody
//            @NotEmpty(message = "list未传") List<TestModel> list
//    ) {
//        return ResultUtil.success(testService.query(null));
//    }

    @ApiOperation(value = "查询", description = "查询")
    @PostMapping("home3")
    public Home3Model home3() {
        return null;
    }
}
