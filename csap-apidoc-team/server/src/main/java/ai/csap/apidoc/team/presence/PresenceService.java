package ai.csap.apidoc.team.presence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

import org.springframework.stereotype.Service;

import ai.csap.apidoc.team.config.TeamProperties;

/**
 * 编辑中提示(契约 5.2 ①):内存、不入库、不写审计。前端打开规则编辑器后每 30 秒续一次,
 * 超过 ttl(默认 90 秒)没续上视为离开;每个用户最多同时 20 条,超出挤掉最早的。单实例部署。
 */
@Service
public class PresenceService {

    static final int MAX_PER_USER = 20;

    private record Key(String env, String service, String ruleKey) {
    }

    private static final class Slot {
        final long userId;
        final String displayName;
        final long since;
        long lastSeen;

        Slot(long userId, String displayName, long since) {
            this.userId = userId;
            this.displayName = displayName;
            this.since = since;
            this.lastSeen = since;
        }
    }

    private final TeamProperties props;
    private final Map<Key, Map<Long, Slot>> slots = new LinkedHashMap<>();
    private LongSupplier clock = System::currentTimeMillis;

    public PresenceService(TeamProperties props) {
        this.props = props;
    }

    /** 仅供测试。 */
    public synchronized void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    private long ttlMs() {
        return Math.max(1, props.getPresence().getTtlSeconds()) * 1000L;
    }

    private void expire(long now) {
        long ttl = ttlMs();
        slots.values().removeIf(m -> {
            m.values().removeIf(s -> now - s.lastSeen > ttl);
            return m.isEmpty();
        });
    }

    public synchronized void touch(String env, String service, String ruleKey, long userId, String displayName) {
        long now = clock.getAsLong();
        expire(now);
        Key k = new Key(env, service, ruleKey);
        Map<Long, Slot> m = slots.computeIfAbsent(k, x -> new LinkedHashMap<>());
        Slot s = m.get(userId);
        if (s == null) {
            m.put(userId, new Slot(userId, displayName, now));
            evictOldest(userId);
        } else {
            s.lastSeen = now;
        }
    }

    private void evictOldest(long userId) {
        while (true) {
            int count = 0;
            Key oldestKey = null;
            long oldest = Long.MAX_VALUE;
            for (Map.Entry<Key, Map<Long, Slot>> e : slots.entrySet()) {
                Slot s = e.getValue().get(userId);
                if (s != null) {
                    count++;
                    if (s.since < oldest) {
                        oldest = s.since;
                        oldestKey = e.getKey();
                    }
                }
            }
            if (count <= MAX_PER_USER || oldestKey == null) {
                return;
            }
            Map<Long, Slot> m = slots.get(oldestKey);
            m.remove(userId);
            if (m.isEmpty()) {
                slots.remove(oldestKey);
            }
        }
    }

    public synchronized void leave(String env, String service, String ruleKey, long userId) {
        Key k = new Key(env, service, ruleKey);
        Map<Long, Slot> m = slots.get(k);
        if (m != null) {
            m.remove(userId);
            if (m.isEmpty()) {
                slots.remove(k);
            }
        }
    }

    /** 某环境、某服务下正在被编辑的规则,不含当前用户自己。 */
    public synchronized List<Map<String, Object>> list(String env, String service, long exceptUserId) {
        expire(clock.getAsLong());
        Map<String, List<Map<String, Object>>> byRule = new TreeMap<>();
        for (Map.Entry<Key, Map<Long, Slot>> e : slots.entrySet()) {
            Key k = e.getKey();
            if (!k.env().equals(env) || !k.service().equals(service)) {
                continue;
            }
            for (Slot s : e.getValue().values()) {
                if (s.userId == exceptUserId) {
                    continue;
                }
                Map<String, Object> u = new LinkedHashMap<>();
                u.put("id", s.userId);
                u.put("displayName", s.displayName);
                u.put("since", s.since);
                byRule.computeIfAbsent(k.ruleKey(), x -> new ArrayList<>()).add(u);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : byRule.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleKey", e.getKey());
            m.put("users", e.getValue());
            out.add(m);
        }
        return out;
    }
}
