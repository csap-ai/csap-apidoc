package ai.csap.validation;


import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import ai.csap.validation.annotation.Phone;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 手机号验证.
 * <p>Created on 2019/8/18
 *
 * @author ycf
 * @since 1.0
 */
public class PhoneValidator implements ConstraintValidator<Phone, String> {

    private boolean required = false;
    /**
     * 定义的手机号验证正则表达式
     */
    private final Pattern pattern = Pattern.compile("1(([38]\\d)|(5[^4&&\\d])|(4[579])|(7[0135678]))\\d{8}");

    @Override
    public void initialize(Phone constraintAnnotation) {
        required = constraintAnnotation.required();
    }

    @Override
    public boolean isValid(String s, ConstraintValidatorContext constraintValidatorContext) {
        if (required) {
            if (StringUtils.isEmpty(s)) {
                return false;
            }
            return pattern.matcher(s).matches();
        }
        return true;
    }
}
