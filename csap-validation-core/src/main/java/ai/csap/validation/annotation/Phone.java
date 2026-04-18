package ai.csap.validation.annotation;


import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import ai.csap.validation.PhoneValidator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 手机号码验证
 *
 * @Author ycf
 * @Date 2019-08-18 01:15
 */
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER})
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = PhoneValidator.class)
public @interface Phone {

    String code() default "";

    boolean required() default true;

    String message() default "手机号码格式错误";

    Class<? extends Payload>[] payload() default {};

}
