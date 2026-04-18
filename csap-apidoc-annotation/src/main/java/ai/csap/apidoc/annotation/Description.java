package ai.csap.apidoc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 描述
 * @dataTime 2019年-12月-29日 15:43:00
 **/
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Description {
    /**
     * 值
     *
     * @return
     */
    String value();


    /**
     * 作者
     *
     * @return
     */
    String author() default "administration";

    /**
     * 时间
     *
     * @return
     */
    String dataTime() default "";

}
