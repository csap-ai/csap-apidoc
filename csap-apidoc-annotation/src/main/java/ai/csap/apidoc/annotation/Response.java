package ai.csap.apidoc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 返回参数注解
 * @dataTime 2019年-12月-27日 17:02:00
 **/
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Response {
    /**
     * 是否包含?包含:不包含
     *
     * @return
     */
    boolean include() default true;

    /**
     * 是否必传
     */
    boolean required() default false;
}
