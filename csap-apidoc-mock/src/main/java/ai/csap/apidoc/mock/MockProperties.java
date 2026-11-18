package ai.csap.apidoc.mock;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内嵌 Mock 配置(csap.apidoc.mock.*)。Mock 地址固定为 /csap/mock/{原路径},不可配置。
 *
 * @author ycf
 * @since 1.4.0
 */
@ConfigurationProperties(prefix = MockProperties.PREFIX)
public class MockProperties {

    public static final String PREFIX = "csap.apidoc.mock";
    /** Mock 路径前缀,固定值 */
    public static final String BASE_PATH = "/csap/mock";
    /** 种子设为 random 表示每次请求随机 */
    public static final String RANDOM = "random";

    /**
     * 总开关,默认关闭;关闭时不注册 /csap/mock/** 和 /csap/mock-status。
     */
    private boolean enabled = false;

    /**
     * 随机种子;random 表示每次请求用新种子(不可复现)。
     */
    private String seed = "csap";

    /**
     * 数组默认长度(0~10)。
     */
    private int arraySize = 2;

    /**
     * 对象嵌套深度上限(1~20)。
     */
    private int maxDepth = 6;

    /**
     * 字段名启发式(email、phone、id 等),关闭后只按类型生成。
     */
    private boolean heuristics = true;

    /**
     * 每次响应前固定等待的毫秒数(调试 loading 状态用)。
     */
    private long delayMs = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSeed() {
        return seed;
    }

    public void setSeed(String seed) {
        this.seed = seed;
    }

    public int getArraySize() {
        return arraySize;
    }

    public void setArraySize(int arraySize) {
        this.arraySize = arraySize;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public boolean isHeuristics() {
        return heuristics;
    }

    public void setHeuristics(boolean heuristics) {
        this.heuristics = heuristics;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
