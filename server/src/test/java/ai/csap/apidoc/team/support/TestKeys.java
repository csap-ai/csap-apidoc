package ai.csap.apidoc.team.support;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;

import ai.csap.apidoc.team.license.LicenseTool;

/** 测试用的临时授权密钥对:每次运行重新生成,私钥只在内存里,不落盘。 */
public final class TestKeys {

    private static final KeyPair PAIR;
    private static final Path PUBLIC_FILE;

    static {
        try {
            PAIR = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            PUBLIC_FILE = Files.createTempFile("csap-team-test-public", ".pem");
            PUBLIC_FILE.toFile().deleteOnExit();
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(PAIR.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            Files.writeString(PUBLIC_FILE, pem, StandardCharsets.US_ASCII);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private TestKeys() {
    }

    public static String publicKeyFile() {
        return PUBLIC_FILE.toString();
    }

    public static PrivateKey privateKey() {
        return PAIR.getPrivate();
    }

    /** 签发授权文本;expiresAt 为毫秒时间戳。 */
    public static String license(String licensee, int maxMembers, long expiresAt) {
        try {
            String payload = "{\"id\":\"test\",\"licensee\":\"" + licensee + "\",\"edition\":\"TEAM\",\"maxMembers\":" + maxMembers
                    + ",\"issuedAt\":" + System.currentTimeMillis() + ",\"expiresAt\":" + expiresAt + "}";
            return LicenseTool.sign(payload, PAIR.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
