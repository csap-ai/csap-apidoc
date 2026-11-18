package ai.csap.apidoc.redis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.apidoc.model.RuleChange;
import ai.csap.apidoc.strategy.store.RuleJson;
import ai.csap.apidoc.strategy.store.RuleStore;

/**
 * Redis 规则存储,数据约定见 docs/design/redis-mode.md(与 csap-apidoc-go 共用)。
 *
 * @author ycf
 * @since 1.4.0
 */
public class RedisRuleStore implements RuleStore {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRuleStore.class);

    /**
     * 保存脚本在 jar 里的位置;与 Go 使用同一段文本
     */
    public static final String SAVE_SCRIPT_RESOURCE = "META-INF/csap-apidoc/redis/save.lua";

    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_:.\\-]*");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile String saveScript;

    private final StringRedisTemplate template;
    private final String prefix;
    private final String service;
    private final int historyLimit;
    private final boolean subscribe;
    private final String rulesKey;
    private final String versionKey;
    private final String changesKey;
    private final String channel;
    private final String historyPrefix;
    private final DefaultRedisScript<Long> script;

    public RedisRuleStore(StringRedisTemplate template, String keyPrefix, String service, int historyLimit,
                          boolean subscribe) {
        this.template = Objects.requireNonNull(template, "template");
        this.prefix = keyPrefix == null ? "csap:apidoc:" : keyPrefix;
        if (!PREFIX.matcher(this.prefix).matches()) {
            throw new IllegalArgumentException("csap.apidoc.redis.key-prefix 只能包含字母、数字和 _ : . -: " + keyPrefix);
        }
        if (service == null || service.trim().isEmpty()) {
            throw new IllegalArgumentException("service 不能为空");
        }
        this.service = service.trim();
        this.historyLimit = historyLimit;
        this.subscribe = subscribe;
        // 服务名用 hash tag 包起来:同一服务的键落在同一个槽,Redis Cluster 下 Lua 脚本也能执行
        String base = this.prefix + "{" + this.service + "}:";
        this.rulesKey = base + "rules";
        this.versionKey = base + "version";
        this.changesKey = base + "changes";
        this.channel = base + "changed";
        this.historyPrefix = base + "history:";
        this.script = new DefaultRedisScript<>(saveScript(), Long.class);
    }

    /**
     * 保存脚本全文
     */
    public static String saveScript() {
        String s = saveScript;
        if (s == null) {
            try (InputStream in = RedisRuleStore.class.getClassLoader().getResourceAsStream(SAVE_SCRIPT_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("缺少 " + SAVE_SCRIPT_RESOURCE);
                }
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                s = new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            saveScript = s;
        }
        return s;
    }

    @Override
    public String service() {
        return service;
    }

    @Override
    public String describe() {
        return "service=" + service + " keyPrefix=" + prefix + " historyLimit=" + historyLimit;
    }

    public String rulesKey() {
        return rulesKey;
    }

    public String versionKey() {
        return versionKey;
    }

    public String changesKey() {
        return changesKey;
    }

    public String channel() {
        return channel;
    }

    public String historyKey(String ruleKey) {
        return historyPrefix + ruleKey;
    }

    @Override
    public void init(boolean createIfMissing, boolean readOnly) {
        try {
            template.execute((org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping());
        } catch (RuntimeException e) {
            throw new IllegalStateException("连接 Redis 失败: " + e.getMessage()
                    + "。请检查 spring.redis.* 配置,Redis 模式用应用已有的 Redis 连接。", e);
        }
    }

    @Override
    public long version() {
        String v = template.opsForValue().get(versionKey);
        return v == null ? 0L : Long.parseLong(v.trim());
    }

    @Override
    public Snapshot load() {
        long v = version();
        Map<String, String> sorted = new java.util.TreeMap<>();
        template.opsForHash().entries(rulesKey).forEach((k, val) -> sorted.put(String.valueOf(k), String.valueOf(val)));
        Map<String, String> rules = new LinkedHashMap<>(sorted);
        return new Snapshot(v, rules);
    }

    @Override
    public long save(String ruleKey, String rulesJson, String updatedBy) {
        String by = updatedBy == null || updatedBy.isEmpty() ? "unknown" : updatedBy;
        // 客户端先按 JSON 结构比较,内容相同不调用脚本
        Object current = template.opsForHash().get(rulesKey, ruleKey);
        if (RuleJson.sameJson(current == null ? null : current.toString(), rulesJson)) {
            return -1L;
        }
        Long v = template.execute(script, java.util.Arrays.asList(rulesKey, versionKey, historyKey(ruleKey), changesKey),
                ruleKey, rulesJson == null ? "" : rulesJson, by, String.valueOf(System.currentTimeMillis()),
                String.valueOf(historyLimit), channel);
        return v == null ? -1L : v;
    }

    @Override
    public List<RuleChange> history(String ruleKey, int limit) {
        int n = limit <= 0 ? 100 : limit;
        List<RuleChange> out = new ArrayList<>();
        if (ruleKey != null && !ruleKey.isEmpty()) {
            for (String json : range(historyKey(ruleKey), n)) {
                out.add(parse(json));
            }
            return out;
        }
        // 不带规则键:遍历 changes 取各键记录合并后按 id 倒序
        Set<String> keys = new LinkedHashSet<>();
        for (Object v : template.opsForHash().values(changesKey)) {
            keys.add(String.valueOf(v));
        }
        for (String key : keys) {
            for (String json : range(historyKey(key), n)) {
                out.add(parse(json));
            }
        }
        out.sort(Comparator.comparingLong(RuleChange::getId).reversed());
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }

    private List<String> range(String key, int n) {
        List<String> list = template.opsForList().range(key, 0, n - 1L);
        return list == null ? Collections.emptyList() : list;
    }

    private static RuleChange parse(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            return new RuleChange(node.get("id").asLong(), node.get("ruleKey").asText(), text(node, "before"),
                    text(node, "after"), node.get("version").asLong(), node.get("updatedBy").asText(),
                    node.get("updatedAt").asLong());
        } catch (IOException e) {
            throw new IllegalStateException("修改记录格式不对: " + json, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    @Override
    public long rollback(long changeId, String updatedBy) {
        Object key = template.opsForHash().get(changesKey, String.valueOf(changeId));
        if (key == null) {
            throw new IllegalArgumentException("修改记录不存在: " + changeId);
        }
        String ruleKey = key.toString();
        for (String json : range(historyKey(ruleKey), historyLimit > 0 ? historyLimit : Integer.MAX_VALUE)) {
            RuleChange c = parse(json);
            if (c.getId() == changeId) {
                return save(ruleKey, c.getBefore(), updatedBy);
            }
        }
        throw new IllegalArgumentException("修改记录不存在: " + changeId);
    }

    @Override
    public AutoCloseable subscribe(Runnable onChange) {
        if (!subscribe) {
            return null;
        }
        RedisConnectionFactory factory = template.getConnectionFactory();
        if (factory == null) {
            return null;
        }
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("csap-apidoc-redis-sub-");
        executor.setDaemon(true);
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.setTaskExecutor(executor);
        container.addMessageListener((message, pattern) -> {
            try {
                onChange.run();
            } catch (RuntimeException e) {
                LOG.warn("csap-apidoc: 处理 Redis 规则变化通知失败: {}", e.toString());
            }
        }, new ChannelTopic(channel));
        container.afterPropertiesSet();
        container.start();
        return () -> {
            container.stop();
            container.destroy();
        };
    }
}
