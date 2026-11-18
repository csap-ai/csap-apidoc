package ai.csap.apidoc.runtime.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import ai.csap.apidoc.runtime.filter.FilterEngine;
import ai.csap.apidoc.runtime.filter.FilterMode;

/**
 * 返回字段过滤配置。
 *
 * <pre>
 * csap:
 *   apidoc:
 *     runtime:
 *       filter:
 *         enabled: true
 *         mode: path            # path | csap
 *         engine: auto          # auto | jackson | fastjson | fastjson2
 *         endpoints:
 *           "[com.example.UserController.get]": off
 * </pre>
 *
 * @author ycf
 * @since 1.2.0
 */
@ConfigurationProperties(prefix = RuntimeFilterProperties.PREFIX)
public class RuntimeFilterProperties {

    public static final String PREFIX = "csap.apidoc.runtime.filter";

    /**
     * 是否开启返回字段过滤(默认关闭)。
     */
    private boolean enabled = false;

    /**
     * 过滤模式:path(路径白名单,与文档显示一致)或 csap(按类型白名单 / 黑名单,兼容 csap-framework)。
     */
    private FilterMode mode = FilterMode.PATH;

    /**
     * 执行过滤的 JSON 库:auto 按接口实际使用的消息转换器选择;指定后只对使用该库输出的响应过滤。
     */
    private FilterEngine engine = FilterEngine.AUTO;

    /**
     * 按接口覆盖模式,键为"类名.方法名"(YAML 里用 "[...]" 包住),值为 off、path 或 csap。
     */
    private Map<String, FilterMode> endpoints = new LinkedHashMap<>();

    /**
     * 接口实际使用的模式
     *
     * @param key 类名.方法名
     * @return 模式
     */
    public FilterMode modeFor(String key) {
        FilterMode override = endpoints.isEmpty() ? null : endpoints.get(key);
        return override != null ? override : mode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public FilterMode getMode() {
        return mode;
    }

    public void setMode(FilterMode mode) {
        this.mode = mode;
    }

    public FilterEngine getEngine() {
        return engine;
    }

    public void setEngine(FilterEngine engine) {
        this.engine = engine;
    }

    public Map<String, FilterMode> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Map<String, FilterMode> endpoints) {
        this.endpoints = endpoints;
    }
}
