package ai.csap.apidoc.team.license;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 团队版授权文件签发工具。
 *
 * <pre>
 * keygen --out &lt;dir&gt;
 * issue  --key &lt;private.pem&gt; --licensee &lt;名称&gt; --max-members &lt;n&gt; --expires 2027-12-31 [--id &lt;id&gt;] [--out &lt;file&gt;]
 * verify --pub &lt;public.pem&gt; &lt;file&gt;
 * </pre>
 *
 * <p>授权文本格式:base64( JSON {"payload":"&lt;授权 JSON 字符串&gt;","signature":"&lt;base64 的 Ed25519 签名&gt;"} ),
 * 签名对象是 payload 字符串的 UTF-8 字节。私钥只写到 {@code --out} 指定的目录,不会打印到终端。
 */
public final class LicenseTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LicenseTool() {
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    /** 可测试入口,返回退出码。 */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0) {
            usage(err);
            return 2;
        }
        try {
            String cmd = args[0];
            switch (cmd) {
                case "keygen":
                    return keygen(Options.parse(args, 1), out);
                case "issue":
                    return issue(Options.parse(args, 1), out);
                case "verify":
                    return verify(Options.parse(args, 1), out, err);
                default:
                    usage(err);
                    return 2;
            }
        } catch (IllegalArgumentException e) {
            err.println("错误: " + e.getMessage());
            return 2;
        } catch (Exception e) {
            err.println("失败: " + e.getMessage());
            return 1;
        }
    }

    private static void usage(PrintStream err) {
        err.println("用法:\n"
                + "  keygen --out <目录>\n"
                + "  issue  --key <private.pem> --licensee <名称> --max-members <人数> --expires <yyyy-MM-dd> [--id <编号>] [--out <文件>]\n"
                + "  verify --pub <public.pem> <授权文件>");
    }

    private static int keygen(Options o, PrintStream out) throws Exception {
        Path dir = Paths.get(o.required("out"));
        Files.createDirectories(dir);
        Path priv = dir.resolve("private.pem");
        Path pub = dir.resolve("public.pem");
        if (Files.exists(priv) || Files.exists(pub)) {
            throw new IllegalArgumentException("目录里已有 private.pem 或 public.pem,不覆盖: " + dir);
        }
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        writePrivate(priv, pem("PRIVATE KEY", kp.getPrivate().getEncoded()));
        Files.writeString(pub, pem("PUBLIC KEY", kp.getPublic().getEncoded()), StandardCharsets.US_ASCII);
        out.println("已生成:");
        out.println("  私钥(妥善保管,不要入库): " + priv);
        out.println("  公钥(替换 server 的 META-INF/csap-team/license-public.pem): " + pub);
        return 0;
    }

    private static void writePrivate(Path p, String pem) throws IOException {
        try {
            Files.createFile(p, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException e) {
            Files.createFile(p);
        }
        Files.writeString(p, pem, StandardCharsets.US_ASCII);
    }

    private static int issue(Options o, PrintStream out) throws Exception {
        PrivateKey key = readPrivate(Paths.get(o.required("key")));
        String licensee = o.required("licensee");
        int max;
        try {
            max = Integer.parseInt(o.required("max-members"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--max-members 必须是整数");
        }
        if (max <= 0) {
            throw new IllegalArgumentException("--max-members 必须大于 0");
        }
        LocalDate exp;
        try {
            exp = LocalDate.parse(o.required("expires"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("--expires 格式应为 yyyy-MM-dd");
        }
        long now = System.currentTimeMillis();
        long expiresAt = exp.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", o.get("id") != null ? o.get("id") : UUID.randomUUID().toString());
        payload.put("licensee", licensee);
        payload.put("edition", "TEAM");
        payload.put("maxMembers", max);
        payload.put("issuedAt", now);
        payload.put("expiresAt", expiresAt);
        String text = sign(MAPPER.writeValueAsString(payload), key);
        if (o.get("out") != null) {
            Files.writeString(Paths.get(o.get("out")), text + "\n", StandardCharsets.US_ASCII);
            out.println("已写入授权文件: " + o.get("out"));
        } else {
            out.println(text);
        }
        return 0;
    }

    private static int verify(Options o, PrintStream out, PrintStream err) throws Exception {
        PublicKey key = readPublic(Paths.get(o.required("pub")));
        if (o.positional.isEmpty()) {
            throw new IllegalArgumentException("缺少授权文件参数");
        }
        String text = Files.readString(Paths.get(o.positional.get(0)), StandardCharsets.UTF_8);
        String payload = verifyText(text, key);
        if (payload == null) {
            err.println("授权无效: 签名校验失败或格式错误");
            return 1;
        }
        out.println("授权有效");
        out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(MAPPER.readTree(payload)));
        return 0;
    }

    /** 签名并编码为授权文本。 */
    public static String sign(String payloadJson, PrivateKey key) throws GeneralSecurityException, IOException {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(key);
        s.update(payloadJson.getBytes(StandardCharsets.UTF_8));
        Map<String, String> env = new LinkedHashMap<>();
        env.put("payload", payloadJson);
        env.put("signature", Base64.getEncoder().encodeToString(s.sign()));
        return Base64.getEncoder().encodeToString(MAPPER.writeValueAsBytes(env));
    }

    /** 校验授权文本,返回 payload JSON;失败返回 null。 */
    public static String verifyText(String text, PublicKey key) {
        try {
            byte[] raw = Base64.getMimeDecoder().decode(text.trim());
            Map<?, ?> env = MAPPER.readValue(raw, Map.class);
            String payload = (String) env.get("payload");
            byte[] sig = Base64.getDecoder().decode((String) env.get("signature"));
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(payload.getBytes(StandardCharsets.UTF_8));
            return s.verify(sig) ? payload : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String pem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    static byte[] unpem(String pem) {
        String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    public static PrivateKey readPrivate(Path p) throws Exception {
        return KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(unpem(Files.readString(p, StandardCharsets.US_ASCII))));
    }

    public static PublicKey readPublic(Path p) throws Exception {
        return KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(unpem(Files.readString(p, StandardCharsets.US_ASCII))));
    }

    private static final class Options {
        final Map<String, String> named = new LinkedHashMap<>();
        final java.util.List<String> positional = new java.util.ArrayList<>();

        static Options parse(String[] args, int from) {
            Options o = new Options();
            for (int i = from; i < args.length; i++) {
                if (args[i].startsWith("--")) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("参数 " + args[i] + " 缺少值");
                    }
                    o.named.put(args[i].substring(2), args[++i]);
                } else {
                    o.positional.add(args[i]);
                }
            }
            return o;
        }

        String get(String k) {
            return named.get(k);
        }

        String required(String k) {
            String v = named.get(k);
            if (v == null || v.isBlank()) {
                throw new IllegalArgumentException("缺少参数 --" + k);
            }
            return v;
        }
    }
}
