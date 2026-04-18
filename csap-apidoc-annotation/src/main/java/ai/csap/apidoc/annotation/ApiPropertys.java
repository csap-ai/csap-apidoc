package ai.csap.apidoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 多个参数的容器注解
 * 仅用于方法级，在一个地方定义多个参数的文档
 * @dataTime 2020年-02月-18日 18:25:00
 **/
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
