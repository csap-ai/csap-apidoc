package ai.csap.apidoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 多个参数的容器注解(方法级)
 * @deprecated 文档生成只读取参数上的 {@link ApiProperty},方法上的这个注解不会生效。
 * 请把 {@link ApiProperty} 直接标注在对应参数上。
 * @dataTime 2020年-02月-18日 18:25:00
 **/
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ApiPropertys {
    /**
     * 多个值
     *
     * @return
     */
    ApiProperty[] value();
}
