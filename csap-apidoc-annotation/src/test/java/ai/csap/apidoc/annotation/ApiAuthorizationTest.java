package ai.csap.apidoc.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 认证类型的示例值必须与类型匹配。
 */
class ApiAuthorizationTest {

    @Test
    void bearerTokenExampleIsBearer() {
        assertEquals("Authorization", ApiAuthorization.BEARER_TOKEN.getKey());
        assertTrue(ApiAuthorization.BEARER_TOKEN.getValue().startsWith("Bearer "));
    }

    @Test
    void basicAuthExampleIsBasic() {
        assertEquals("Authorization", ApiAuthorization.BASIC_AUTH.getKey());
        assertTrue(ApiAuthorization.BASIC_AUTH.getValue().startsWith("Basic "));
    }
}
