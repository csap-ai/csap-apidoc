package ai.csap.apidoc.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 数据类型强制验证
 * @dataTime 2020年-09月-03日 15:49:00
 **/
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
@Documented
public @interface DataTypeValidate {
    /**
     * 返回code
     *
     * @return
     */
    String code() default "400";

    /**
     * 返回message
     *
     * @return
     */
    String message() default "数据格式错误";

    /**
     * 返回data数据
     *
     * @return
     */
    String data() default "";
}
