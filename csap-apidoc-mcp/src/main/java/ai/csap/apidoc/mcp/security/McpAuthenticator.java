package ai.csap.apidoc.mcp.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ai.csap.apidoc.mcp.autoconfigure.McpProperties;

/**
 * MCP 端点鉴权
 *
 * <ul>
 *   <li>token:{@code Authorization: Bearer <token>} 必须匹配配置的某个令牌;没有配置任何令牌时一律拒绝(fail-closed)</li>
 *   <li>passthrough:不校验,调用方请求头转发给目标接口,由目标接口自己鉴权</li>
 * </ul>
 *
 * @author ycf
 * @since 1.3.0
 */
public class McpAuthenticator {

    private final List<NamedDigest> tokens;

    public McpAuthenticator(List<McpProperties.Token> tokens) {
        List<NamedDigest> list = new ArrayList<>();
        if (tokens != null) {
            int i = 1;
            for (McpProperties.Token t : tokens) {
                if (t == null || t.getValue() == null || t.getValue().trim().isEmpty()) {
                    continue;
                }
                String name = t.getName() == null || t.getName().trim().isEmpty() ? "token-" + i : t.getName().trim();
                list.add(new NamedDigest(name, sha256(t.getValue().trim())));
                i++;
            }
        }
        this.tokens = Collections.unmodifiableList(list);
    }

    /**
     * 鉴权结果
     */
    public static final class Result {
        private final boolean ok;
        private final String caller;
        private final String reason;

        private Result(boolean ok, String caller, String reason) {
            this.ok = ok;
            this.caller = caller;
            this.reason = reason;
        }

        public boolean isOk() {
            return ok;
        }

        /**
         * 令牌名称;透传模式为来源 IP
         */
        public String getCaller() {
            return caller;
        }

        public String getReason() {
            return reason;
        }
    }

    public boolean hasTokens() {
        return !tokens.isEmpty();
    }

    public List<String> tokenNames() {
        List<String> names = new ArrayList<>();
        tokens.forEach(t -> names.add(t.name));
        return names;
    }

    /**
     * @param mode          生效的鉴权方式
     * @param authorization Authorization 请求头
     * @param remoteAddress 来源 IP
     * @return 结果
     */
    public Result authenticate(McpProperties.AuthMode mode, String authorization, String remoteAddress) {
        String ip = remoteAddress == null ? "unknown" : remoteAddress;
        if (mode == McpProperties.AuthMode.PASSTHROUGH) {
            return new Result(true, ip, null);
        }
        if (tokens.isEmpty()) {
            return new Result(false, ip, "MCP 令牌未配置(csap.apidoc.mcp.auth.tokens)");
        }
        if (authorization == null) {
            return new Result(false, ip, "缺少 Authorization: Bearer 令牌");
        }
        String a = authorization.trim();
        if (a.length() < 7 || !a.substring(0, 7).toLowerCase(Locale.ROOT).equals("bearer ")) {
            return new Result(false, ip, "Authorization 不是 Bearer 令牌");
        }
        byte[] given = sha256(a.substring(7).trim());
        String matched = null;
        // 逐个比较且不提前退出,避免按令牌位置泄露时序
        for (NamedDigest t : tokens) {
            if (MessageDigest.isEqual(t.digest, given) && matched == null) {
                matched = t.name;
            }
        }
        return matched == null ? new Result(false, ip, "令牌无效") : new Result(true, matched, null);
    }

    static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class NamedDigest {
        final String name;
        final byte[] digest;

        NamedDigest(String name, byte[] digest) {
            this.name = name;
            this.digest = digest;
        }
    }
}
