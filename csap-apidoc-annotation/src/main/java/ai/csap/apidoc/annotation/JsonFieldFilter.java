package ai.csap.apidoc.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * json字段过滤
 * @author ycf
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonFieldFilter {

    /**
     * 对哪个类的属性进行过滤
     *
     * @return
     */
    Class<?> type() default Object.class;

    /**
     * 包含哪些字段，即哪些字段可以显示
     *
     * @return
     */
    String[] include() default {};

    /**
     * 不包含哪些字段，即哪些字段不可以显示
     *
     * @return
     */
    String[] exclude() default {};
}
