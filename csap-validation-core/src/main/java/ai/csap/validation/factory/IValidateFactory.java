package ai.csap.validation.factory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintValidatorContext;

/**
 * 标准的验证接口
 *
 * @Author ycf
 * @Date 2025/9/9 15:51
 * @Version 1.0
 */
public interface IValidateFactory extends IValidateBaseFactory {
    /**
     * 验证参数
     *
     * @param args            参数
     * @param method          方法
     * @param controllerClass 当前类
     * @param paramClass      参数类型
     * @param parameterName   参数名
     */
    void validation(Object args, Method method, Class<?> controllerClass, Class<?> paramClass, String parameterName, ConstraintValidatorContext validatorContext);

    /**
     * 验证当前级联业务
     *
     * @param validateField 当前的级联验证信息
     * @param value         验证的数据
     * @param function      验证函数，key is fieldName, return value is field value
     */
    Validate.ValidateField validatorsChildren(String parameterName,
                                               Map<String, Validate.ValidateField> validateField,
                                               Object value, Object childrenValue,
                                               BiFunctionValidation<String, List<String>, Object, Object> function,
                                               ConstraintValidatorContext validatorContext);

}
