package ai.csap.apidoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description api实体类
 * @dataTime 2019年-12月-26日 17:12:00
 **/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ApiModel {
    /**
     * 实体类的名称
     *
     * @return
     */
    String value() default "";

    /**
     * 描述
     */
    String description() default "";

    /**
     * 标签
     *
     * @return
     */
    String[] tags() default {};

    /**
     * 分组
     *
     * @return
     */
    String[] group() default "default";

    /**
     * 版本
     *
     * @return
     */
    String[] version() default "default";

    /**
     * 是否必传【如果必传，请求入参 此对象不可为null，包括list泛型参数】
     *
     * @return
     */
    boolean required() default true;
}
