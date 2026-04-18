package ai.csap.validation.factory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;

import ai.csap.apidoc.type.ModelType;
import ai.csap.apidoc.util.ReflectionKit;
import ai.csap.validation.properties.ValidationProperties;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import jakarta.validation.ConstraintValidatorContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 验证工厂类.
 * <p>Created on 2021/1/22
 *
 * @author yangchengfu
 * @since 1.0
 */
@Slf4j
@AllArgsConstructor
@Getter
public class DefaultValidateFactoryImpl implements IValidateFactory {
    /**
     * 注解验证处理类
     */
    private ConstraintHelper constraintHelper;
    /**
     * 验证提示类型
     */
    private ValidationProperties validationProperties;


    /**
     * 验证参数
     *
     * @param args            参数
     * @param method          方法
     * @param controllerClass 当前类
     * @param paramClass      参数类型
     * @param parameterName   参数名
     */
    public void validation(Object args, Method method, Class<?> controllerClass, Class<?> paramClass, String parameterName, ConstraintValidatorContext validatorContext) {
        //TODO 基础验证
        Validate.ValidateField validateField = request(controllerClass, method, paramClass.getSimpleName());
        if (validateField == null) {
            return;
        }
        //TODO 验证当前数据
        validateField.validators(args, validationProperties.getTipType(), validatorContext);
        if (validateField.getModelType().equals(ModelType.BASE_DATA)) {
            if (validateField.getChildren().containsKey(parameterName)) {
                validateField.getChildren().get(parameterName).validators(args, validationProperties.getTipType(), validatorContext);
            }
        } else {
            //TODO 级联验证
            validatorsChildren(parameterName, validateField.getChildren(), args, null, ReflectionKit::getValidateValue, validatorContext);
        }
    }

    /**
     * 验证当前级联业务
     *
     * @param validateField 当前的级联验证信息
     * @param value         验证的数据
     * @param function      验证函数，key is fieldName, return value is field value
     */
    public Validate.ValidateField validatorsChildren(String parameterName,
                                                      Map<String, Validate.ValidateField> validateField,
                                                      Object value, Object childrenValue,
                                                      BiFunctionValidation<String, List<String>, Object, Object> function,
                                                      ConstraintValidatorContext validatorContext) {
        return validateField
                .entrySet()
                .stream()
                .map(e -> validatorsField(parameterName, e, childrenValue != null ? childrenValue : value, function, validatorContext))
                .filter(ObjUtil::isNotEmpty)
                .filter(i -> !i.getMapData().getValue().getChildren().isEmpty())
                .map(e -> validatorsChildren(parameterName, e.getMapData().getValue().getChildren(), e.getValue(), childrenValue, function, validatorContext))
                .filter(ObjectUtil::isNotEmpty)
                .findFirst()
                .orElse(null);
    }

    /**
     * 验证字段
     *
     * @param validateFieldEntry map字段验证信息
     * @param value              当前之前的值
     * @param function           验证函数
     * @param parameterName      参数名称
     * @return 自定义数据对象
     */
    private StoreData validatorsField(String parameterName,
                                       Map.Entry<String, Validate.ValidateField> validateFieldEntry,
                                       Object value,
                                       BiFunctionValidation<String, List<String>, Object, Object> function,
                                       ConstraintValidatorContext validatorContext) {
        Object values = value;
        if (!ModelType.BASE_DATA.equals(validateFieldEntry.getValue().getModelType()) && values instanceof Collection) {
            for (Object i : (Collection<?>) values) {
                Map<String, Validate.ValidateField> fieldMap = MapUtil.<String, Validate.ValidateField>builder()
                        .put(validateFieldEntry.getKey(), validateFieldEntry.getValue()).build();
                validatorsChildren(parameterName, fieldMap, i, null, function, validatorContext);
            }
            return null;
        } else {
            if (ModelType.BASE_DATA.equals(validateFieldEntry.getValue().getModelType())) {
                validateFieldEntry.getValue().validators(values, validationProperties.getTipType(), validatorContext);
            } else {
                values = function.apply(validateFieldEntry.getKey(), null, values);
                validateFieldEntry.getValue().validators(values, validationProperties.getTipType(), validatorContext);
            }
        }
        return StoreData.builder()
                .mapData(validateFieldEntry)
                .value(values)
                .build();
    }
}
