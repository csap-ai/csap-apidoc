package ai.csap.validation.properties;

/**
 * 非必填字段上的校验规则(Pattern、NotEmpty、注解规则等)怎么处理。
 *
 * @author ycf
 * @since 1.4.0
 */
public enum OptionalFieldRules {
    /**
     * 没传值(null)时不校验,传了值就校验(默认,与 Go 版一致)
     */
    VALIDATE,
    /**
     * 非必填字段一律不校验(1.2.0 ~ 1.3.0 的行为,用于升级时临时回退)
     */
    SKIP
}
