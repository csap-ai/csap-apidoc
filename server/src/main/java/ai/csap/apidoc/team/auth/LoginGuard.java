package ai.csap.apidoc.team.auth;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 登录失败锁定(内存,单实例):
 * <ul>
 *   <li>同一账号(不区分大小写)+ 同一来源 IP 连续失败 5 次,锁 15 分钟;</li>
 *   <li>同一账号 15 分钟内所有 IP 合计失败 50 次,也锁 15 分钟(防分布式猜测)。</li>
 * </ul>
 * 按账号 + IP 计数,知道账号名的人从别处乱试不会把真正的用户锁住。
 */
@Component
public class LoginGuard {

    static final int MAX_FAILURES = 5;
    static final int MAX_ACCOUNT_FAILURES = 50;
    static final long LOCK_MS = 15 * 60 * 1000L;

    private static final class State {
        int failures;
        long lockedUntil;
    }

    private static final class AccountState {
        final ArrayDeque<Long> failures = new ArrayDeque<>();
        long lockedUntil;
    }

    private final ConcurrentHashMap<String, State> pairs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AccountState> accounts = new ConcurrentHashMap<>();

    public boolean isLocked(String username, String ip, long now) {
        State s = pairs.get(key(username, ip));
        if (s != null) {
            synchronized (s) {
                if (s.lockedUntil > now) {
                    return true;
                }
                if (s.lockedUntil != 0) {
                    s.lockedUntil = 0;
                    s.failures = 0;
                }
            }
        }
        AccountState a = accounts.get(norm(username));
        if (a != null) {
            synchronized (a) {
                if (a.lockedUntil > now) {
                    return true;
                }
            }
        }
        return false;
    }

    public void fail(String username, String ip, long now) {
        State s = pairs.computeIfAbsent(key(username, ip), k -> new State());
        synchronized (s) {
            s.failures++;
            if (s.failures >= MAX_FAILURES) {
                s.lockedUntil = now + LOCK_MS;
            }
        }
        AccountState a = accounts.computeIfAbsent(norm(username), k -> new AccountState());
        synchronized (a) {
            a.failures.addLast(now);
            while (!a.failures.isEmpty() && a.failures.peekFirst() < now - LOCK_MS) {
                a.failures.pollFirst();
            }
            if (a.failures.size() >= MAX_ACCOUNT_FAILURES) {
                a.lockedUntil = now + LOCK_MS;
                a.failures.clear();
            }
        }
    }

    /** 登录成功:只清除该账号 + IP 的计数,账号总计数自然过期。 */
    public void success(String username, String ip) {
        pairs.remove(key(username, ip));
    }

    private static String norm(String u) {
        return u == null ? "" : u.toLowerCase();
    }

    private static String key(String u, String ip) {
        return norm(u) + "\u0000" + (ip == null ? "" : ip);
    }
}
