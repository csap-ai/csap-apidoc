package ai.csap.apidoc.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.model.CsapDocMethod;

/**
 * {@link ApiDocService#forMapping}:不写 method 的 @RequestMapping 记录为 GET/POST/PUT/DELETE;
 * 没有映射注解的方法不能空指针。
 */
class ApiDocServiceForMappingTest {

    @SuppressWarnings("unused")
    static class Fixture {
        @ApiOperation("任意方式")
        @RequestMapping("/any")
        public void any() {
        }

        @ApiOperation("GET")
        @GetMapping("/get")
        public void get() {
        }

        @ApiOperation("没有映射注解")
        public void noMapping() {
        }
    }

    private static CsapDocMethod run(String name) throws Exception {
        CsapDocMethod docMethod = CsapDocMethod.builder().methods(new ArrayList<>()).build();
        return ApiDocService.forMapping(Fixture.class.getMethod(name), docMethod);
    }

    @Test
    void requestMappingWithoutMethodListsEachMethodOnceInOrder() throws Exception {
        CsapDocMethod docMethod = run("any");
        assertEquals(List.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE),
                docMethod.getMethods());
        assertArrayEquals(new String[]{"/any"}, docMethod.getPaths());
    }

    @Test
    void explicitMappingKeepsDeclaredMethod() throws Exception {
        assertEquals(List.of(RequestMethod.GET), run("get").getMethods());
    }

    @Test
    void apiOperationWithoutMappingIsSkippedInsteadOfNpe() throws Exception {
        // 不抛空指针;methods 为空,ApiDocService#method 据此跳过该方法
        assertTrue(run("noMapping").getMethods().isEmpty());
    }
}
