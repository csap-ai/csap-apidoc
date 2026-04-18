package ai.csap.validation.properties;

import ai.csap.validation.type.ValidationTipType;

import lombok.Data;

/**
 * 验证配置.
 * <p>Created on 2020/1/6
 *
 * @author yangchengfu
 * @since 1.0
 */
@Data
public class ValidationProperties {
    public static final String PREFIX = "csap.validation";
    /**
     * 错误码的名称
     */
    private String code = "code";
    /**
     * 错误描述的名称
     */
    private String message = "message";

    /**
     * 是否可用
     */
    private Boolean enabled = Boolean.TRUE;

    /**
     * 验证提示的类型
     */
    private ValidationTipType tipType = ValidationTipType.TIP_1;
}
