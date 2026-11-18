package ai.csap.apidoc.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * jar 内的建表 SQL 必须与 {@link DatabaseRuleStore#schemaScript} 一致。
 * 改了建表代码后,用 {@code -Dcsap.apidoc.generate-sql=true} 运行本测试会重新生成这些文件。
 */
class SchemaSqlFilesTest {

    @Test
    void sqlFilesMatchCode() throws Exception {
        boolean generate = Boolean.getBoolean("csap.apidoc.generate-sql");
        for (DatabaseDialect dialect : DatabaseDialect.values()) {
            String name = dialect.name().toLowerCase();
            String expected = DatabaseRuleStore.schemaScript(dialect, "csap_apidoc_");
            if (generate) {
                Path dir = Paths.get("src/main/resources/META-INF/csap-apidoc/database");
                Files.createDirectories(dir);
                Files.write(dir.resolve(name + ".sql"), expected.getBytes(StandardCharsets.UTF_8));
            }
            try (InputStream in = getClass().getClassLoader()
                                            .getResourceAsStream("META-INF/csap-apidoc/database/" + name + ".sql")) {
                if (in == null && generate) {
                    continue; // 刚生成,classpath 里还没有,下一次运行校验
                }
                assertNotNull(in, "缺少 META-INF/csap-apidoc/database/" + name + ".sql");
                assertEquals(expected, new String(readAll(in), StandardCharsets.UTF_8),
                        name + ".sql 与代码不一致,请用 -Dcsap.apidoc.generate-sql=true 重新生成");
            }
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
