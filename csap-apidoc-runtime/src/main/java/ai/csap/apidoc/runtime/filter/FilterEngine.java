package ai.csap.apidoc.runtime.filter;

/**
 * 执行过滤的 JSON 库。
 *
 * @author ycf
 * @since 1.2.0
 */
public enum FilterEngine {
    /**
     * 按接口实际使用的消息转换器自动选择
     */
    AUTO,
    /**
     * Jackson(Spring 默认)
     */
    JACKSON,
    /**
     * fastjson 1.x
     */
    FASTJSON,
    /**
     * fastjson2
     */
    FASTJSON2
}
