package ai.csap.apidoc.annotation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiStatusTest {

    @Test
    void resolveReturnsConstant() {
        for (ApiStatus status : ApiStatus.values()) {
            assertSame(status, ApiStatus.resolve(status.name()));
            assertTrue(status.matches(status.name()));
        }
    }

    @Test
    void resolveUnknownOrNullReturnsNull() {
        assertNull(ApiStatus.resolve("NOPE"));
        assertNull(ApiStatus.resolve(null));
        assertFalse(ApiStatus.FINISH.matches("DEFAULT"));
    }
}
