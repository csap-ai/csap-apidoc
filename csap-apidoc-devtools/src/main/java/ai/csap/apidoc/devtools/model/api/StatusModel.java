package ai.csap.apidoc.devtools.model.api;

import ai.csap.apidoc.annotation.ApiStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设置接口状态的请求体。status 为 null 表示清除覆盖(回到注解 / 默认)。
 *
 * @author ycf
 * @since 1.4.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusModel {
    private String className;
    private String methodName;
    /**
     * DEFAULT(开发中)/ FINISH(完成)/ DEPRECATED(过时),null 清除
     */
    private ApiStatus status;
    /**
     * 备注,如替代接口、下线时间
     */
    private String statusNote;
}
