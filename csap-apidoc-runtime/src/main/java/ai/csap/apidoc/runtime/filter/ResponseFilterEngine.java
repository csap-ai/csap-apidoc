package ai.csap.apidoc.runtime.filter;

/**
 * 某个 JSON 库的过滤实现:把要写出的响应体包装成该库在序列化时会应用过滤的形式。
 *
 * @author ycf
 * @since 1.2.0
 */
public interface ResponseFilterEngine {

    /**
     * 引擎类型
     */
    FilterEngine type();

    /**
     * 是否处理该消息转换器写出的响应
     *
     * @param converterType Spring MVC 选中的消息转换器类型
     */
    boolean supports(Class<?> converterType);

    /**
     * 包装响应体
     *
     * @param body    即将写出的响应体(可能是外层包装后的对象)
     * @param session 本次响应的过滤状态
     * @return 交给消息转换器写出的对象
     */
    Object apply(Object body, FilterSession session);
}
