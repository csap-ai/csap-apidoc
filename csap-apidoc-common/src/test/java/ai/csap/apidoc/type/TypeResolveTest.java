package ai.csap.apidoc.type;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * ModelType / CamelCaseType 的 resolve 必须返回对应常量(原先 MAPPINGS 里存的是 null)。
 */
class TypeResolveTest {

    @Test
    void modelTypeResolve() {
        for (ModelType type : ModelType.values()) {
            assertSame(type, ModelType.resolve(type.name()));
            assertTrue(type.matches(type.name()));
        }
        assertNull(ModelType.resolve("NOPE"));
        assertNull(ModelType.resolve(null));
        assertFalse(ModelType.ARRAY.matches("OBJECT"));
    }

    @Test
    void camelCaseTypeResolveIsCaseInsensitive() {
        for (CamelCaseType type : CamelCaseType.values()) {
            assertSame(type, CamelCaseType.resolve(type.name()));
            assertSame(type, CamelCaseType.resolve(type.name().toUpperCase()));
            assertTrue(type.matches(type.name()));
        }
        assertNull(CamelCaseType.resolve("nope"));
    }
}
