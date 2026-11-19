package ai.csap.apidoc.team.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import ai.csap.apidoc.team.config.TeamProperties;
import jakarta.servlet.http.HttpServletRequest;

/** 请求来源 IP;只有来自 {@code csap.team.trusted-proxies} 的请求才信任 X-Forwarded-For。 */
@Component
public class ClientIp {

    private final List<Cidr> trusted = new ArrayList<>();

    public ClientIp(TeamProperties props) {
        for (String s : props.getTrustedProxies()) {
            if (s != null && !s.isBlank()) {
                trusted.add(Cidr.parse(s.trim()));
            }
        }
    }

    public String resolve(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        if (trusted.isEmpty() || !isTrusted(remote)) {
            return remote;
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remote;
        }
        String[] parts = xff.split(",");
        // 从右往左,跳过可信代理,第一个不可信的就是客户端
        for (int i = parts.length - 1; i >= 0; i--) {
            String ip = parts[i].trim();
            if (!isTrusted(ip)) {
                return ip;
            }
        }
        return parts[0].trim();
    }

    boolean isTrusted(String ip) {
        for (Cidr c : trusted) {
            if (c.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    static final class Cidr {
        private final byte[] net;
        private final int bits;

        private Cidr(byte[] net, int bits) {
            this.net = net;
            this.bits = bits;
        }

        static Cidr parse(String s) {
            if (!s.matches("[0-9a-fA-F:.]+(/[0-9]{1,3})?")) {
                throw new IllegalArgumentException("csap.team.trusted-proxies 配置无效(只接受 IP 或 CIDR): " + s);
            }
            try {
                String[] p = s.split("/");
                byte[] addr = InetAddress.getByName(p[0]).getAddress();
                int bits = p.length > 1 ? Integer.parseInt(p[1]) : addr.length * 8;
                return new Cidr(addr, bits);
            } catch (UnknownHostException | RuntimeException e) {
                throw new IllegalArgumentException("csap.team.trusted-proxies 配置无效: " + s);
            }
        }

        boolean matches(String ip) {
            byte[] a;
            try {
                if (ip == null || !ip.matches("[0-9a-fA-F:.]+")) {
                    return false;
                }
                a = InetAddress.getByName(ip).getAddress();
            } catch (UnknownHostException e) {
                return false;
            }
            if (a.length != net.length) {
                return false;
            }
            int full = bits / 8;
            for (int i = 0; i < full; i++) {
                if (a[i] != net[i]) {
                    return false;
                }
            }
            int rem = bits % 8;
            if (rem == 0) {
                return true;
            }
            int mask = 0xFF << (8 - rem);
            return (a[full] & mask) == (net[full] & mask);
        }
    }
}
