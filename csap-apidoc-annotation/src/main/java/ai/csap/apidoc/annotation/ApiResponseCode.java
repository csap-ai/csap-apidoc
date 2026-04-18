package ai.csap.apidoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description api返回的code说明
 * @dataTime 2019年-12月-28日 17:48:00
 **/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ApiResponseCode {
    /**
     * 描述
     *
     * @return 结果
     */
    String value();

    /**
     * 分组
     *
     * @return 结果
     */
    String[] group() default "default";

    /**
     * 版本
     *
     * @return 结果
     */
    String[] version() default "default";
}
