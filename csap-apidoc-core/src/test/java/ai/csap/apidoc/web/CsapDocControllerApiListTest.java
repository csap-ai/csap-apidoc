package ai.csap.apidoc.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;

/**
 * {@code /csap/apidoc/{tableName}?key=} 只能在副本上过滤,不能改坏共享的文档缓存。
 */
class CsapDocControllerApiListTest {

    private static final String CONTROLLER = "com.example.UserController";

    private static CsapDocMethod method(String key) {
        CsapDocMethod m = new CsapDocMethod();
        m.setKey(key);
        return m;
    }

    private static CsapDocResponse cachedResponse() {
        CsapDocModelController user = new CsapDocModelController();
        user.setName(CONTROLLER);
        List<CsapDocMethod> methods = new ArrayList<>();
        methods.add(method(CONTROLLER + ".get"));
        methods.add(method(CONTROLLER + ".list"));
        methods.add(method(CONTROLLER + ".delete"));
        user.setMethodList(methods);

        CsapDocModelController other = new CsapDocModelController();
        other.setName("com.example.OrderController");
        other.setMethodList(new ArrayList<>(List.of(method("com.example.OrderController.get"))));

        CsapDocResponse response = new CsapDocResponse();
        response.getApiList().add(user);
        response.getApiList().add(other);
        return response;
    }

    @Test
    void filteringByKeyDoesNotMutateCachedControllers() {
        CsapDocResponse cache = cachedResponse();
        CsapDocModelController cachedUser = cache.getApiList().get(0);
        List<CsapDocMethod> cachedMethods = cachedUser.getMethodList();
        CsapDocController controller = new CsapDocController(null, null);

        CsapDocResponse filtered = controller.apiList(cache, CONTROLLER, CONTROLLER + ".list");

        // 返回结果只有一个 Controller 和一个方法
        assertEquals(1, filtered.getApiList().size());
        assertEquals(1, filtered.getApiList().get(0).getMethodList().size());
        assertEquals(CONTROLLER + ".list", filtered.getApiList().get(0).getMethodList().get(0).getKey());
        assertNotSame(cachedUser, filtered.getApiList().get(0));

        // 缓存原样不动
        assertEquals(2, cache.getApiList().size());
        assertSame(cachedMethods, cachedUser.getMethodList());
        assertEquals(3, cachedUser.getMethodList().size());
        assertEquals(1, cache.getApiList().get(1).getMethodList().size());

        // 再查一次别的方法,仍然能从完整缓存里找到
        CsapDocResponse again = controller.apiList(cache, CONTROLLER, CONTROLLER + ".get");
        assertEquals(1, again.getApiList().get(0).getMethodList().size());
        assertEquals(3, cachedUser.getMethodList().size());
    }

    @Test
    void withoutKeyReturnsCacheAsIs() {
        CsapDocResponse cache = cachedResponse();
        CsapDocResponse result = new CsapDocController(null, null).apiList(cache, CONTROLLER, null);
        assertSame(cache, result);
        assertEquals(3, cache.getApiList().get(0).getMethodList().size());
    }
}
