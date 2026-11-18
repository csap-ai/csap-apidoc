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
 * @deprecated 自 1.3.0 起弃用,将在 2.0 删除。按接口选择显示字段,请改用 YAML 或 SQLite 规则
 * ({@code @EnableApidoc(paramType = ...)});需要在所有接口里显示的字段用
 * {@link ApiModelProperty#forceReq()} / {@link ApiModelProperty#forceRep()}。1.3.0 中行为不变。
 **/
@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Deprecated
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
