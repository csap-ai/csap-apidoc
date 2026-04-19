package ai.csap.apidoc.devtools.proxy;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author yangchengfu
 * @since 1.0
 */
class HostAllowListTest {

    @Test
    void emptyListDeniesEverything() {
        HostAllowList list = new HostAllowList(Collections.emptyList());
        assertTrue(list.isEmpty());
        assertFalse(list.isAllowed("https://api.example.com/whatever"));
        assertFalse(list.isAllowed("http://localhost:8080/x"));

        HostAllowList nullList = new HostAllowList(null);
        assertTrue(nullList.isEmpty());
        assertFalse(nullList.isAllowed("https://api.example.com/x"));
    }

    @Test
    void exactLiteralMatch() {
        HostAllowList list = new HostAllowList(Collections.singletonList("api.example.com"));
        assertTrue(list.isAllowed("https://api.example.com/v1/orders"));
        assertFalse(list.isAllowed("https://other.example.com/x"));
        assertFalse(list.isAllowed("https://api.example.com.evil.com/x"));
    }

    @Test
    void prefixWildcardMatchesSubdomainsButNotApex() {
        HostAllowList list = new HostAllowList(Collections.singletonList("*.example.com"));
        assertTrue(list.isAllowed("https://api.example.com/x"));
        assertTrue(list.isAllowed("https://nested.api.example.com/x"));
        assertFalse(list.isAllowed("https://example.com/x"),
                "bare apex must NOT match *.example.com");
        assertFalse(list.isAllowed("https://notexample.com/x"),
                "*.example.com must not match a different apex that ends with example.com");
    }

    @Test
    void hostMatchIsCaseInsensitive() {
        HostAllowList list = new HostAllowList(Arrays.asList("API.Example.COM", "*.STAGING.example.com"));
        assertTrue(list.isAllowed("https://api.example.com/x"));
        assertTrue(list.isAllowed("https://API.EXAMPLE.com/x"));
        assertTrue(list.isAllowed("https://orders.staging.example.com/x"));
        assertTrue(list.isAllowed("https://Orders.Staging.Example.Com/x"));
    }

    @Test
    void localhostAndLoopbackAllowedWhenListed() {
        HostAllowList list = new HostAllowList(Arrays.asList("localhost", "127.0.0.1"));
        assertTrue(list.isAllowed("http://localhost:8080/api"));
        assertTrue(list.isAllowed("http://127.0.0.1:9000/api"));
        assertFalse(list.isAllowed("http://10.0.0.1/api"));
    }

    @Test
    void portInUrlIsIgnoredForMatching() {
        HostAllowList list = new HostAllowList(Collections.singletonList("api.example.com"));
        assertTrue(list.isAllowed("https://api.example.com:8443/x"));
        assertTrue(list.isAllowed("http://api.example.com:80/x"));
        assertTrue(list.isAllowed("https://api.example.com/x"));
    }

    @Test
    void invalidOrEmptyUrlsAreDenied() {
        HostAllowList list = new HostAllowList(Collections.singletonList("api.example.com"));
        assertFalse(list.isAllowed(null));
        assertFalse(list.isAllowed(""));
        assertFalse(list.isAllowed("not-a-url"),
                "URI without a host must not match a literal host entry");
        assertFalse(list.isAllowed("https://"));
    }

    @Test
    void blanksAndNullsInConfigAreIgnored() {
        HostAllowList list = new HostAllowList(Arrays.asList(null, "", "   ", "api.example.com"));
        assertFalse(list.isEmpty());
        assertTrue(list.isAllowed("https://api.example.com/x"));
        assertFalse(list.isAllowed("https://other.example.com/x"));
    }
}
