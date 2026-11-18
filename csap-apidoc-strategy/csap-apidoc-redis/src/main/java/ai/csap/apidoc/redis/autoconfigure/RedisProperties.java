package ai.csap.apidoc.redis.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 模式配置(只有 {@code @EnableApidoc(paramType = ApiStrategyType.REDIS)} 时才会用到)。
 *
 * @author ycf
 * @since 1.4.0
 */
@ConfigurationProperties(prefix = RedisProperties.PREFIX)
public class RedisProperties {

    public static final String PREFIX = "csap.apidoc.redis";

    /**
     * 键前缀;服务名用 hash tag 包在前缀后面,如 csap:apidoc:{order-service}:rules。
     */
    private String keyPrefix = "csap:apidoc:";

    /**
     * 服务标识;为空时用 spring.application.name,再为空用 default。
     */
    private String service;

    /**
     * 每个接口最多保留的修改记录条数。
     */
    private int historyLimit = 200;

    /**
     * 轮询规则版本号的间隔(订阅断开时的兜底),发现变化就重新加载;0 表示不轮询。
     */
    private Duration pollInterval = Duration.ofSeconds(5);

    /**
     * 是否订阅 Redis 频道,别的实例保存后即时重新加载。
     */
    private boolean subscribe = true;

    /**
     * 只读模式:只读取规则,不允许 DevTools 保存和回滚。
     */
    private boolean readOnly = false;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    public boolean isSubscribe() {
        return subscribe;
    }

    public void setSubscribe(boolean subscribe) {
        this.subscribe = subscribe;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
