package ai.csap.apidoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 头部文件
 * @dataTime 2020年-02月-28日 23:48:00
 **/
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Headers {

    /**
     * 值
     *
     * @return
     */
    String value();

    /**
     * 键
     *
     * @return
     */
    String key();

    /**
     * 是否必传
     */
    boolean required() default false;


    /**
     * 示例.
     * 头部文件
     * 多示例：Content-Type=application/json,Accept=text/plain
     * 单示例：Content-Type=application/json
     */
    String example() default "";

    /**
     * 描述
     *
     * @return
     */
    String description() default "";


    /**
     * 位置
     */
    int position() default 0;

    /**
     * 是否隐藏
     */
    boolean hidden() default false;

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
}
