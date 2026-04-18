package ai.csap.apidoc.scanner;

import java.lang.reflect.Method;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

import ai.csap.apidoc.annotation.DocAuth;
import ai.csap.apidoc.annotation.DocGlobalHeader;
import ai.csap.apidoc.model.CsapDocAuthHint;
import ai.csap.apidoc.model.CsapDocGlobalHeaderHint;

/**
 * M7 — 校验 {@link DocHintsCollector} 对 @DocGlobalHeader / @DocAuth 注解的
 * method &gt; class &gt; package 三级合并语义。
 *
 * @author yangchengfu
 */
public class DocHintsCollectorM7Test {

    // ---------- fixtures ----------

    @DocGlobalHeader(name = "X-Class-Header", description = "from class", example = "c-1", required = true)
    @DocGlobalHeader(name = "X-Shared", description = "class wins by default", example = "c-shared")
    @DocAuth(scheme = "basic", description = "class default")
    static class ClassFixture {

        @DocGlobalHeader(name = "X-Method-Header", description = "from method", example = "m-1")
        @DocGlobalHeader(name = "X-Shared", description = "method overrides class", example = "m-shared", required = true)
        @DocAuth(scheme = "bearer", description = "method overrides")
        public void annotated() {
        }

        public void inheritOnly() {
        }
    }

    @DocAuth(scheme = "apikey", in = "header", name = "X-API-Key")
    static class ApiKeyFixture {
        public void anything() {
        }
    }

    @DocAuth(scheme = "totally-unknown")
    static class UnknownSchemeFixture {
        public void anything() {
        }
    }

    static class BareFixture {
        public void anything() {
        }
    }

    // ---------- helpers ----------

    private static Method method(Class<?> cls, String name) throws NoSuchMethodException {
        return cls.getDeclaredMethod(name);
    }

    // ---------- tests ----------

    @Test
    public void methodAnnotationOverridesClassByName() throws Exception {
        List<CsapDocGlobalHeaderHint> hints =
                DocHintsCollector.collectGlobalHeaderHints(ClassFixture.class, method(ClassFixture.class, "annotated"));

        Assert.assertEquals(hints.size(), 3,
                "Expected merged set to contain X-Class-Header, X-Shared, X-Method-Header");

        CsapDocGlobalHeaderHint shared = hints.stream()
                .filter(h -> "X-Shared".equals(h.getName()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        Assert.assertEquals(shared.getDescription(), "method overrides class",
                "method-level @DocGlobalHeader must win over class-level for same name");
        Assert.assertTrue(shared.isRequired(), "method-level required=true must propagate");

        Assert.assertTrue(hints.stream().anyMatch(h -> "X-Class-Header".equals(h.getName())),
                "class-level header missing from merge");
        Assert.assertTrue(hints.stream().anyMatch(h -> "X-Method-Header".equals(h.getName())),
                "method-level header missing from merge");
    }

    @Test
    public void methodWithoutOwnAnnotationsInheritsClass() throws Exception {
        List<CsapDocGlobalHeaderHint> hints =
                DocHintsCollector.collectGlobalHeaderHints(ClassFixture.class, method(ClassFixture.class, "inheritOnly"));

        Assert.assertEquals(hints.size(), 2);
        Assert.assertTrue(hints.stream().anyMatch(h -> "X-Class-Header".equals(h.getName())));
        Assert.assertTrue(hints.stream().anyMatch(h -> "X-Shared".equals(h.getName())));
    }

    @Test
    public void barePojoYieldsEmptyList() throws Exception {
        List<CsapDocGlobalHeaderHint> hints =
                DocHintsCollector.collectGlobalHeaderHints(BareFixture.class, method(BareFixture.class, "anything"));
        Assert.assertNotNull(hints);
        Assert.assertTrue(hints.isEmpty(), "Bare class+method must produce empty list, never null");

        Assert.assertNull(DocHintsCollector.resolveAuthHint(BareFixture.class, method(BareFixture.class, "anything")),
                "Bare class+method must produce null auth hint");
    }

    @Test
    public void methodAuthOverridesClassAuth() throws Exception {
        CsapDocAuthHint hint =
                DocHintsCollector.resolveAuthHint(ClassFixture.class, method(ClassFixture.class, "annotated"));
        Assert.assertNotNull(hint);
        Assert.assertEquals(hint.getScheme(), "bearer");
        Assert.assertEquals(hint.getDescription(), "method overrides");
        Assert.assertNull(hint.getIn(), "non-apikey scheme must not expose 'in'");
        Assert.assertNull(hint.getName(), "non-apikey scheme must not expose 'name'");
    }

    @Test
    public void classAuthAppliedWhenMethodAbsent() throws Exception {
        CsapDocAuthHint hint =
                DocHintsCollector.resolveAuthHint(ClassFixture.class, method(ClassFixture.class, "inheritOnly"));
        Assert.assertNotNull(hint);
        Assert.assertEquals(hint.getScheme(), "basic");
    }

    @Test
    public void apiKeySchemeKeepsInAndName() throws Exception {
        CsapDocAuthHint hint =
                DocHintsCollector.resolveAuthHint(ApiKeyFixture.class, method(ApiKeyFixture.class, "anything"));
        Assert.assertNotNull(hint);
        Assert.assertEquals(hint.getScheme(), "apikey");
        Assert.assertEquals(hint.getIn(), "header");
        Assert.assertEquals(hint.getName(), "X-API-Key");
    }

    @Test
    public void unknownSchemeIsAcceptedButLogged() throws Exception {
        // Should not throw — the scanner only emits a WARN per the spec.
        CsapDocAuthHint hint =
                DocHintsCollector.resolveAuthHint(UnknownSchemeFixture.class,
                        method(UnknownSchemeFixture.class, "anything"));
        Assert.assertNotNull(hint);
        Assert.assertEquals(hint.getScheme(), "totally-unknown",
                "Scanner must surface the unknown scheme verbatim, not coerce");
    }

    @Test
    public void supportedSchemesContainsExpectedSet() {
        Assert.assertTrue(DocHintsCollector.supportedSchemes().contains("bearer"));
        Assert.assertTrue(DocHintsCollector.supportedSchemes().contains("basic"));
        Assert.assertTrue(DocHintsCollector.supportedSchemes().contains("apikey"));
        Assert.assertTrue(DocHintsCollector.supportedSchemes().contains("oauth2_client"));
        Assert.assertTrue(DocHintsCollector.supportedSchemes().contains("none"));
    }
}
