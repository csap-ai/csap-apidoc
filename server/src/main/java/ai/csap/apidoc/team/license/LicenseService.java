package ai.csap.apidoc.team.license;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.common.ApiException;
import ai.csap.apidoc.team.common.Json;
import ai.csap.apidoc.team.config.TeamProperties;
import ai.csap.apidoc.team.db.Db;
import ai.csap.apidoc.team.db.Settings;
import com.fasterxml.jackson.databind.JsonNode;

/** 授权:Ed25519 签名校验、人数限制。没有授权或无效 = 试用(最多 5 个活跃用户);过期不锁数据,只限制新增。 */
@Service
public class LicenseService {

    public static final int TRIAL_MAX = 5;
    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    static final String SETTING = "license.text";

    private final Settings settings;
    private final Db db;
    private final TeamProperties props;
    private volatile PublicKey publicKey;
    private volatile boolean keyLoaded;

    public LicenseService(Settings settings, Db db, TeamProperties props) {
        this.settings = settings;
        this.db = db;
        this.props = props;
    }

    public record Info(String status, String id, String licensee, int maxMembers, Long expiresAt, long activeMembers) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", status);
            m.put("licensee", licensee);
            m.put("maxMembers", maxMembers);
            m.put("expiresAt", expiresAt);
            m.put("activeMembers", activeMembers);
            return m;
        }
    }

    private PublicKey publicKey() {
        if (!keyLoaded) {
            synchronized (this) {
                if (!keyLoaded) {
                    publicKey = loadKey();
                    keyLoaded = true;
                }
            }
        }
        return publicKey;
    }

    private PublicKey loadKey() {
        try {
            String pem;
            String file = props.getLicensePublicKeyFile();
            if (file != null && !file.isBlank()) {
                pem = Files.readString(Paths.get(file), StandardCharsets.US_ASCII);
            } else {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/csap-team/license-public.pem")) {
                    if (in == null) {
                        return null;
                    }
                    pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
                }
            }
            String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (IOException | java.security.GeneralSecurityException | IllegalArgumentException e) {
            log.warn("授权公钥无法读取,按未授权(试用)处理: {}", e.getMessage());
            return null;
        }
    }

    private String text() {
        String t = settings.get(SETTING);
        if (t != null && !t.isBlank()) {
            return t;
        }
        String file = props.getLicenseFile();
        if (file != null && !file.isBlank()) {
            try {
                return Files.readString(Paths.get(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("授权文件无法读取: {}", file);
            }
        }
        return null;
    }

    /** 校验授权文本,返回 payload;签名或格式无效返回 null。 */
    JsonNode verify(String text) {
        PublicKey key = publicKey();
        if (key == null || text == null) {
            return null;
        }
        try {
            JsonNode env = Json.read(new String(Base64.getMimeDecoder().decode(text.trim()), StandardCharsets.UTF_8));
            String payload = env.get("payload").asText();
            byte[] sig = Base64.getDecoder().decode(env.get("signature").asText());
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(payload.getBytes(StandardCharsets.UTF_8));
            if (!s.verify(sig)) {
                return null;
            }
            JsonNode p = Json.read(payload);
            return p != null && p.has("maxMembers") && p.has("expiresAt") ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 测试用:校验授权文本,返回 payload JSON 文本,无效返回 null。 */
    public String verifyForTest(String text) {
        JsonNode p = verify(text);
        return p == null ? null : p.toString();
    }

    public Info current() {
        long active = activeMembers();
        String text = text();
        if (text == null || text.isBlank()) {
            return new Info("TRIAL", null, null, TRIAL_MAX, null, active);
        }
        JsonNode p = verify(text);
        if (p == null) {
            return new Info("INVALID", null, null, TRIAL_MAX, null, active);
        }
        long exp = p.get("expiresAt").asLong();
        boolean expired = exp < System.currentTimeMillis();
        return new Info(expired ? "EXPIRED" : "VALID", p.path("id").asText(null), p.path("licensee").asText(null),
                p.get("maxMembers").asInt(), exp, active);
    }

    public int effectiveLimit() {
        Info i = current();
        return "VALID".equals(i.status()) ? i.maxMembers() : TRIAL_MAX;
    }

    public long activeMembers() {
        return db.count("SELECT COUNT(*) FROM csap_team_user WHERE status = 'ACTIVE'");
    }

    /** 新增 / 启用一个活跃用户前调用,超出人数抛 LICENSE_LIMIT。 */
    public void checkCanAddActive() {
        int limit = effectiveLimit();
        if (activeMembers() >= limit) {
            throw ApiException.licenseLimit("活跃用户已达授权上限 " + limit + " 人,请升级授权或停用其他用户");
        }
    }

    /** 安装授权(控制台上传),签名无效拒绝。 */
    public Info install(String text) {
        if (text == null || text.isBlank()) {
            throw ApiException.validation("授权内容不能为空");
        }
        if (verify(text) == null) {
            throw ApiException.validation("授权无效:签名校验失败或格式错误");
        }
        settings.set(SETTING, text.trim());
        return current();
    }
}
