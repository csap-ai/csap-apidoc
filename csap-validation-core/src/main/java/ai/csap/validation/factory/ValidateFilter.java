package ai.csap.validation.factory;

import static ai.csap.apidoc.util.ApidocClazzUtils.BASIC_DATA_TYPE_MAP;
import static ai.csap.apidoc.util.IValidate.CODE;
import static ai.csap.apidoc.util.IValidate.DEFAULT_CODE;
import static ai.csap.apidoc.util.IValidate.MESSAGE;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

import ai.csap.validation.constraint.ConstraintResolver;
import ai.csap.validation.constraint.ConstraintValidatorAdapter;
import ai.csap.validation.constraint.MessageTemplates;
import ai.csap.validation.type.ValidationStrategyType;

import cn.hutool.core.util.ArrayUtil;

/**
 * 方法过滤.
 * <p>Created on 2021/1/22
 *
 * @author yangchengfu
 * @since 1.0
 */
public interface ValidateFilter extends ValidateData {

    Logger LOGS = LoggerFactory.getLogger(ValidateFilter.class);

    /**
     * 获取验证对象
     *
     * @return 返回验证类
     */
    ConstraintHelper getConstraintHelper();

    /**
     * 获取当前字段所有验证逻辑.
     * <p>同时识别 jakarta.validation 与 javax.validation 的约束注解,见 {@link ConstraintResolver}。
     *
     * @param fieldClass  字段class
     * @param annotations 注解
     * @return 自定义验证列表
     */
    default List<Validate.ConstraintValidatorField> getAllFieldConstraintValidator(Class<?> fieldClass, Annotation[] annotations) {
        if (fieldClass == null || ArrayUtil.isEmpty(annotations)) {
            return new ArrayList<>();
        }
        List<Validate.ConstraintValidatorField> list = new ArrayList<>();
        for (Annotation annotation : annotations) {
            ConstraintValidatorAdapter adapter = ConstraintResolver.resolve(getConstraintHelper(), fieldClass, annotation);
            if (adapter != null) {
                list.add(new Validate.ConstraintValidatorField()
                        .setValidator(adapter)
                        .setAnnotation(annotation)
                        .setCode(code(annotation))
                        .setType(ValidationStrategyType.Annotation)
                        .setMessage(MessageTemplates.interpolate(message(annotation), annotation)));
            }
        }
        return list;
    }

    /**
     * 查找Class
     *
     * @param cl       具体对象 《子类》
     * @param contains 需要查找的对象《父类》
     * @return 是否存在
     */
    default boolean containsClass(Class<?> cl, Class<?> contains) {
        if (cl == null) {
            return false;
        }
        if (BASIC_DATA_TYPE_MAP.containsKey(cl.getName())) {
            cl = BASIC_DATA_TYPE_MAP.get(cl.getName());
        }
        return contains.isAssignableFrom(cl);
    }

    /**
     * 获取提示编码
     *
     * @param annotation 注解
     * @return 编码
     */
    default String code(Annotation annotation) {
        try {
            Object code = AnnotationUtils.getValue(annotation, CODE);
            if (code != null && !code.toString().isEmpty()) {
                return code.toString();
            }
        } catch (Exception ignored) {
            // 注解没有 code 属性
        }
        return DEFAULT_CODE;
    }

    /**
     * 获取消息提示
     *
     * @param annotation 注解
     * @return 描述
     */
    default String message(Annotation annotation) {
        try {
            Object message = AnnotationUtils.getValue(annotation, MESSAGE);
            if (message != null) {
                return message.toString();
            }
        } catch (Exception ignored) {
        }
        return DEFAULT_CODE;
    }
}
