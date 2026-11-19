package ai.csap.apidoc.team.license;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseToolTest {

    private int run(ByteArrayOutputStream out, ByteArrayOutputStream err, String... args) {
        return LicenseTool.run(args, new PrintStream(out), new PrintStream(err));
    }

    @Test
    void keygenIssueVerify(@TempDir Path dir) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(0, run(out, err, "keygen", "--out", dir.toString()));
        assertTrue(Files.exists(dir.resolve("private.pem")));
        // 终端输出不含私钥内容
        assertFalse(out.toString().contains("BEGIN PRIVATE KEY"));
        // 不覆盖已有密钥
        assertEquals(2, run(out, err, "keygen", "--out", dir.toString()));

        Path lic = dir.resolve("team.lic");
        assertEquals(0, run(out, err, "issue", "--key", dir.resolve("private.pem").toString(), "--licensee", "示例公司",
                "--max-members", "50", "--expires", "2027-12-31", "--out", lic.toString()));
        out.reset();
        assertEquals(0, run(out, err, "verify", "--pub", dir.resolve("public.pem").toString(), lic.toString()));
        String text = out.toString();
        assertTrue(text.contains("示例公司"));
        assertTrue(text.contains("\"maxMembers\" : 50"));
        assertTrue(text.contains("TEAM"));
    }

    @Test
    void tamperedLicenseFails(@TempDir Path dir) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        run(out, err, "keygen", "--out", dir.resolve("a").toString());
        run(out, err, "keygen", "--out", dir.resolve("b").toString());
        Path lic = dir.resolve("x.lic");
        run(out, err, "issue", "--key", dir.resolve("a/private.pem").toString(), "--licensee", "甲", "--max-members", "5",
                "--expires", "2027-01-01", "--out", lic.toString());
        // 用另一对密钥的公钥验证 -> 失败
        assertEquals(1, run(out, err, "verify", "--pub", dir.resolve("b/public.pem").toString(), lic.toString()));
        assertNotNull(LicenseTool.verifyText(Files.readString(lic), LicenseTool.readPublic(dir.resolve("a/public.pem"))));
        assertNull(LicenseTool.verifyText("bm90IGEgbGljZW5zZQ==", LicenseTool.readPublic(dir.resolve("a/public.pem"))));
    }

    @Test
    void badArguments() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        assertEquals(2, run(out, err));
        assertEquals(2, run(out, err, "issue", "--licensee", "x"));
        assertEquals(2, run(out, err, "unknown"));
    }
}
