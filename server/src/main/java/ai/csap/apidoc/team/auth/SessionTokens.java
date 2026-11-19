package ai.csap.apidoc.team.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.db.Settings;
import com.fasterxml.jackson.databind.JsonNode;

/** 会话令牌:HS256 签名(密钥存 setting {@code token.signing_key},首次启动随机生成)。 */
@Component
public class SessionTokens {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final Settings settings;
    private volatile byte[] key;

    public SessionTokens(Settings settings) {
        this.settings = settings;
    }

    private byte[] key() {
        byte[] k = key;
        if (k == null) {
            String v = settings.get("token.signing_key");
            if (v == null) {
                throw new IllegalStateException("令牌签名密钥尚未生成");
            }
            k = Base64.getDecoder().decode(v);
            key = k;
        }
        return k;
    }

    public record Claims(long userId, long issuedAt, long expiresAt, String pv) {
    }

    public String issue(long userId, String pv, long now, long ttlMs) {
        String payload = B64.encodeToString(Json.write(Map.of("uid", userId, "iat", now, "exp", now + ttlMs, "pv", pv))
                .getBytes(StandardCharsets.UTF_8));
        String body = HEADER + "." + payload;
        return body + "." + B64.encodeToString(hmac(body));
    }

    /** 校验签名与有效期;无效返回 null。 */
    public Claims verify(String token, long now) {
        if (token == null) {
            return null;
        }
        String[] p = token.split("\\.");
        if (p.length != 3) {
            return null;
        }
        byte[] expect = hmac(p[0] + "." + p[1]);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(p[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!MessageDigest.isEqual(expect, actual)) {
            return null;
        }
        try {
            JsonNode n = Json.read(new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8));
            if (n == null || !n.has("uid") || !n.has("exp")) {
                return null;
            }
            long exp = n.get("exp").asLong();
            if (exp <= now) {
                return null;
            }
            return new Claims(n.get("uid").asLong(), n.path("iat").asLong(), exp, n.path("pv").asText(""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
