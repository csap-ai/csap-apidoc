package ai.csap.apidoc.mcp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.mcp.autoconfigure.McpProperties;

class McpAuthenticatorTest {

    @Test
    void tokenMode() {
        McpAuthenticator a = new McpAuthenticator(Arrays.asList(new McpProperties.Token("ide", "test-token-1"),
                new McpProperties.Token(null, "test-token-2"), new McpProperties.Token("empty", " ")));
        assertEquals(Arrays.asList("ide", "token-2"), a.tokenNames());
        McpAuthenticator.Result ok = a.authenticate(McpProperties.AuthMode.TOKEN, "Bearer test-token-1", "1.2.3.4");
        assertTrue(ok.isOk());
        assertEquals("ide", ok.getCaller());
        assertEquals("token-2", a.authenticate(McpProperties.AuthMode.TOKEN, "bearer test-token-2", null).getCaller());
        assertFalse(a.authenticate(McpProperties.AuthMode.TOKEN, "Bearer wrong", "1.2.3.4").isOk());
        assertFalse(a.authenticate(McpProperties.AuthMode.TOKEN, null, "1.2.3.4").isOk());
        assertFalse(a.authenticate(McpProperties.AuthMode.TOKEN, "Basic dGVzdA==", "1.2.3.4").isOk());
        assertEquals("1.2.3.4", a.authenticate(McpProperties.AuthMode.TOKEN, "Bearer wrong", "1.2.3.4").getCaller());
    }

    @Test
    void failClosedWithoutTokens() {
        McpAuthenticator a = new McpAuthenticator(Collections.emptyList());
        assertFalse(a.authenticate(McpProperties.AuthMode.TOKEN, "Bearer ", "1.2.3.4").isOk());
        assertFalse(a.authenticate(McpProperties.AuthMode.TOKEN, "Bearer anything", "1.2.3.4").isOk());
    }

    @Test
    void passthroughUsesIpAsCaller() {
        McpAuthenticator.Result r = new McpAuthenticator(null).authenticate(McpProperties.AuthMode.PASSTHROUGH, null, "10.0.0.1");
        assertTrue(r.isOk());
        assertEquals("10.0.0.1", r.getCaller());
    }
}
