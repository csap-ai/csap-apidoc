package ai.csap.apidoc.core;

import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;

/**
 * 接口执行前接口
 *
 * @Author ycf
 * @Date 2022/4/27 4:49 PM
 * @Version 1.0
 */
public interface ApiMethodHandle extends Ordered {
    /**
     * order排序,越小越靠前
     *
     * @return 排序
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 执行
     *
     * @param parameter 参数
     * @param value     数据
     */
    void resolve(MethodParameter parameter, Object value);
}
