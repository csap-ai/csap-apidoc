package ai.csap.apidoc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 参数分组
 * @dataTime 2019年-12月-29日 15:58:00
 **/
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Group {
    /**
     * 请求分组，标识统一http的请求字段:【接口的方法名称！！】
     *
     * @return
     */
    String value();

    /**
     * 请求注解
     *
     * @return
     */
    Request request() default @Request(value = false);

    /**
     * 返回注解
     *
     * @return
     */
    Response response() default @Response(include = false);

}
