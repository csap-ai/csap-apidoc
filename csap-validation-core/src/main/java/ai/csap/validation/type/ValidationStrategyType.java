package ai.csap.validation.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 验证
 *
 * @Author ycf
 * @Date 2021/9/29 6:08 下午
 * @Version 1.0
 */
@AllArgsConstructor
@Getter
public enum ValidationStrategyType {
    //验证类型
    NotNull("NotNull", "不能为空"),
    NotEmpty("NotEmpty", "不能为空字符串"),
    Pattern("Pattern", "正则表达式"),
    Annotation("Annotation", "注解验证"),
    CUSTOMER("customer", "自定义验证");
    private final String key;
    private final String descr;

}
