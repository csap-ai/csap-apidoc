package ai.csap.apidoc.annotation;




import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 请求参数注解
 * @dataTime 2019年-12月-27日 17:02:00
 **/
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Request {
    /**
     * 参数类型
     * <p>
     * Valid values are {@code path}, {@code query}, {@code body},
     * {@code header} or {@code form}.
     */
    ParamType paramType() default ParamType.QUERY;

    /**
     * 是否请求存在这个参数
     *
     * @return
     */
    boolean value() default true;

    /**
     * 是否必传
     */
    boolean required() default false;

}
