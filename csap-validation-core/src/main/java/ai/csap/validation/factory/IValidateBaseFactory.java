package ai.csap.validation.factory;

import static ai.csap.apidoc.util.IValidate.DOT;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.hutool.core.collection.CollectionUtil;

/**
 * 数据验证工厂类型.
 * <p>Created on 2021/1/22
 *
 * @author yangchengfu
 * @since 1.0
 */
public interface IValidateBaseFactory extends ValidateFilter {
    /**
     * 验证缓存对象
     * <p>
     * key is className  add methodName
     * value is method validate info
     * <p/>
     */
    Map<String, Validate> VALIDATE_MAP = new ConcurrentHashMap<>(16);

    /**
     * 方法获取验证参数
     *
     * @param controller class类
     * @param method     方法
     * @return
     */
    default Validate method(Class<?> controller, Method method) {
        return method(controller.getName() + DOT + method.getName());
    }

    /**
     * 方法获取验证参数
     *
     * @param controller class类
     * @param method     方法
     * @return
     */
    default Validate method(String controller, String method) {
        return method(controller + DOT + method);
    }

    /**
     * 方法获取验证参数
     * <p>controllerName+methodName<p/>
     *
     * @param classNameAndMethodName 键
     * @return
     */
    default Validate method(String classNameAndMethodName) {
        return VALIDATE_MAP.computeIfAbsent(classNameAndMethodName, k -> new Validate());
    }

    /**
     * 方法获取验证参数
     *
     * @param controller class类
     * @param method     方法
     * @param field      键名称
     * @return 返回当前字段需要验证的列表
     */
    default Validate.ValidateField request(Class<?> controller, Method method, String field) {
        return method(controller, method).field(field);
    }

    /**
     * 方法获取验证参数-直接获取目标参数
     *
     * @param controller    class类
     * @param method        方法
     * @param field         键名称
     * @param parameterName 参数名称
     * @return 返回当前字段需要验证的列表
     */
    default Validate.ValidateField request(Class<?> controller, Method method, String field, String parameterName) {
        return method(controller, method).field(field).getChildren().get(parameterName);
    }

    /**
     * 添加请求验证参数
     *
     * @param key      键名称
     * @param fieldKey 字段名称
     * @return 返回当前字段需要验证的列表
     */
    default Validate.ValidateField addRequest(String key, String fieldKey, String fieldRemark, List<Validate.ConstraintValidatorField> list) {
        return method(key).addValidateField(fieldKey, fieldRemark, list);
    }

    /**
     * 方法获取验证参数
     *
     * @param controller class类
     * @param method     方法
     * @return 返回过滤对象
     */
    default List<FilterClassParam> response(Class<?> controller, Method method) {
        return method(controller, method).getFilterClassParams();
    }

    /**
     * 方法获取验证参数
     *
     * @param classNameAndMethodName 键名称
     * @param modelClass             参数对象
     * @return 返回过滤对象
     */
    default FilterClassParam addResponse(String classNameAndMethodName, Class<?> modelClass) {
        return method(classNameAndMethodName).filterResponse(modelClass);
    }

    /**
     * 清空
     *
     * @param classNameAndMethodName 拼接名称
     */
    default void clear(String classNameAndMethodName) {
        method(classNameAndMethodName).clear();
    }

    /**
     * 添加返回参数
     *
     * @param classNameAndMethodName 键名称
     * @param field                  字段
     */
    default FilterClassParam addIncludes(String classNameAndMethodName, String field, Class<?> modelClass) {
        return addResponse(classNameAndMethodName, modelClass).addIncludes(field);
    }

    /**
     * 添加过滤返回参数
     *
     * @param classNameAndMethodName 键名称
     * @param field                  字段
     */
    default FilterClassParam addExcludes(String classNameAndMethodName, String field, Class<?> modelClass) {
        return addResponse(classNameAndMethodName, modelClass).addExcludes(field);
    }

    /**
     * 添加请求参数验证
     *
     * @param key         键
     * @param fileKeyName 字段
     */
    default Validate.ValidateField addRequestValidate(String key, String fileKeyName, String fieldRemark, Annotation[] annotations, String fieldName, BaseModel baseModel) {
        return addRequestValidate(key, fileKeyName, fieldName, fieldRemark, baseModel, getAllFieldConstraintValidator(baseModel.getField(), annotations));
    }

    /**
     * 添加请求参数验证
     *
     * @param key         键
     * @param fileKeyName 字段键名称
     * @param fieldName   字段名称
     * @param fieldRemark 字段描述
     * @param baseModel   当前model
     * @param list        验证列表
     * @return 验证信息
     */
    default Validate.ValidateField addRequestValidate(String key, String fileKeyName, String fieldName,
                                                       String fieldRemark, BaseModel baseModel,
                                                       List<Validate.ConstraintValidatorField> list) {
        //TODO 获取当前字段验证规则集合
        if (CollectionUtil.isEmpty(list)) {
            return null;
        }
        return baseModel.getValidateField() != null ?
                addValidateField(fieldName, fieldRemark, baseModel.getValidateField(), list) :
                method(key)
                        .addValidateField(fileKeyName, baseModel)
                        .putValidateField(fieldName, fieldRemark, list, baseModel);

    }

    /**
     * 添加验证的字段
     *
     * @param fieldKey             字段标识
     * @param fieldRemark          字段描述
     * @param validateField        字段验证
     * @param constraintValidators 验证逻辑
     * @return 验证逻辑
     */
    default Validate.ValidateField addValidateField(String fieldKey, String fieldRemark,
                                                     Map<String, Validate.ValidateField> validateField,
                                                     List<Validate.ConstraintValidatorField> constraintValidators) {
        return validateField.computeIfAbsent(fieldKey, k -> new Validate.ValidateField()
                .constraintValidators(constraintValidators)
                .fieldName(fieldKey)
                .remark(fieldRemark));
    }


}
