package ai.csap.apidoc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import ai.csap.apidoc.model.CsapDocAuthHint;
import ai.csap.apidoc.model.CsapDocGlobalHeaderHint;
import ai.csap.apidoc.model.CsapDocMethod;

import lombok.SneakyThrows;

/**
 * 验证 M7 的 {@code globalHeaderHints} / {@code authHint} 字段在 YAML
 * ({@code docType=yaml}) 模式下可以零代码改造直接 round-trip 到
 * {@link CsapDocMethod}。
 * <p>
 * 这是 M7 多源策略说明（见 {@code DocHintsCollector} 类注释）的"YAML 路径"
 * 等价性证明 —— 框架已支持，只是没有正式样例。
 * </p>
 *
 * @author yangchengfu
 * @since 1.x M7
 */
public class YamlHintsTest {

    @SneakyThrows
    @Test
    public void hintsBindFromYaml() {
        ClassLoader cl = getClass().getClassLoader();
        String path = URLDecoder.decode(
                cl.getResource("application-hints-method.yaml").getPath(),
                StandardCharsets.UTF_8);

        Map<String, Map<String, CsapDocMethod>> tree = new YAMLMapper().readValue(
                new FileInputStream(path),
                new TypeReference<Map<String, Map<String, CsapDocMethod>>>() {
                });

        Map<String, CsapDocMethod> ctrl = tree.get("ai.csap.apidoc.web.YamlController");
        assertNotNull("controller node missing", ctrl);

        // GET getYaml — bearer + 2 global headers
        CsapDocMethod get = ctrl.get("getYaml");
        assertNotNull("getYaml not bound", get);

        List<CsapDocGlobalHeaderHint> headers = get.getGlobalHeaderHints();
        assertNotNull("globalHeaderHints missing", headers);
        assertEquals(2, headers.size());

        CsapDocGlobalHeaderHint tenant = headers.get(0);
        assertEquals("X-Tenant-Id", tenant.getName());
        assertEquals("tenant-demo", tenant.getExample());
        assertTrue("X-Tenant-Id should be required", tenant.isRequired());

        CsapDocGlobalHeaderHint trace = headers.get(1);
        assertEquals("X-Trace-Id", trace.getName());
        assertFalse("X-Trace-Id should be optional", trace.isRequired());

        CsapDocAuthHint auth = get.getAuthHint();
        assertNotNull("authHint missing", auth);
        assertEquals("bearer", auth.getScheme());
        assertNull("bearer scheme must not carry 'in'", auth.getIn());
        assertNull("bearer scheme must not carry 'name'", auth.getName());

        // PUT putYaml — apikey scheme with in/name populated
        CsapDocMethod put = ctrl.get("putYaml");
        assertNotNull("putYaml not bound", put);

        CsapDocAuthHint apikey = put.getAuthHint();
        assertNotNull("apikey authHint missing", apikey);
        assertEquals("apikey", apikey.getScheme());
        assertEquals("query", apikey.getIn());
        assertEquals("api_key", apikey.getName());
    }
}
