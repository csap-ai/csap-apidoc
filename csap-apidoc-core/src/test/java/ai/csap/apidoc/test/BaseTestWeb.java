package ai.csap.apidoc.test;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2019年-12月-28日 18:49:00
 **/
public class BaseTestWeb<S, T, M, ID> {
    public TestService<M> testService = new TestService<>();

//    @ApiOperation(value = "修改", httpMethod = HttpMethod.PUT)
//    @PutMapping("update")
//    public void findById(ID id) {
//    }

//    @ApiOperation(value = "查询", httpMethod = HttpMethod.GET)
//    @GetMapping("queryManagerPage")
//    public ResponseManagerModel<M> queryManagerPage(M model) {
//        return null;
//    }
}
