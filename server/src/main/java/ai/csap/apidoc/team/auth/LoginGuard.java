package ai.csap.apidoc.team.auth;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** 登录失败锁定:连续失败 5 次锁 15 分钟(内存计数,按用户名,含不存在的用户名)。 */
@Component
public class LoginGuard {

    static final int MAX_FAILURES = 5;
    static final long LOCK_MS = 15 * 60 * 1000L;

    private static final class State {
        int failures;
        long lockedUntil;
    }

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public boolean isLocked(String username, long now) {
        State s = states.get(key(username));
        if (s == null) {
            return false;
        }
        synchronized (s) {
            if (s.lockedUntil > now) {
                return true;
            }
            if (s.lockedUntil != 0) {
                s.lockedUntil = 0;
                s.failures = 0;
            }
            return false;
        }
    }

    public void fail(String username, long now) {
        State s = states.computeIfAbsent(key(username), k -> new State());
        synchronized (s) {
            s.failures++;
            if (s.failures >= MAX_FAILURES) {
                s.lockedUntil = now + LOCK_MS;
            }
        }
    }

    public void success(String username) {
        states.remove(key(username));
    }

    private static String key(String u) {
        return u == null ? "" : u.toLowerCase();
    }
}
