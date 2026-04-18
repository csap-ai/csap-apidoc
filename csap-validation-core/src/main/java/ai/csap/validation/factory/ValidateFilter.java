package ai.csap.validation.factory;

import static ai.csap.apidoc.util.ApidocClazzUtils.BASIC_DATA_TYPE_MAP;
import static ai.csap.apidoc.util.IValidate.CODE;
import static ai.csap.apidoc.util.IValidate.DEFAULT_CODE;
import static ai.csap.apidoc.util.IValidate.MESSAGE;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorDescriptor;
import org.hibernate.validator.internal.metadata.core.ConstraintHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import ai.csap.validation.type.ValidationStrategyType;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;

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
     * 根据注解 获取验证业务处理类
     *
     * @param annotationType 字段注解
     * @param <A>            注解类型
     * @return 验证列表
     */
    default <A extends Annotation> List<ConstraintValidatorDescriptor<A>> getAllValidatorDescriptors(Class<A> annotationType) {
        List<ConstraintValidatorDescriptor<A>> list = getConstraintHelper().getAllValidatorDescriptors(annotationType);
        if (CollectionUtil.isEmpty(list)) {
            list = Lists.newArrayList();
        }
        return list;
    }

    /**
     * 获取当前字段所有验证逻辑
     *
     * @param fieldClass  字段class
     * @param annotations 注解
     * @return 自定义验证列表
     */
    default List<Validate.ConstraintValidatorField> getAllFieldConstraintValidator(Class<?> fieldClass, Annotation[] annotations) {
        return Stream.of(annotations)
                .filter(i -> i.annotationType().isAnnotationPresent(Constraint.class))
                .map(i -> getAllValidatorDescriptors(i.annotationType())
                        .stream()
                        .map(ConstraintValidatorDescriptor::getValidatorClass)
                        .filter(i2 -> containsClass(fieldClass, (Class<?>) getClass(i2)))
                        .map(i3 -> validator(i3, i))
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream)
                .filter(i -> containsClass(fieldClass, (Class<?>) getClass(i.getValidator().getClass())))
                .collect(Collectors.toList());
    }

    /**
     * 获取验证的目标Class
     *
     * @param clss 注解类型
     * @return 实际类型
     */
    default Type getClass(Class<? extends ConstraintValidator> clss) {
        try {
            Type[] classes = clss.getGenericInterfaces();
            if (ArrayUtil.isNotEmpty(classes)) {
                return ((ParameterizedType) classes[0]).getActualTypeArguments()[1];
            } else {
                return ((ParameterizedType) clss.getGenericSuperclass()).getActualTypeArguments()[0];
            }
        } catch (Exception e) {
            LOGS.error(e.getMessage(), e);
        }
        return null;
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
     * 获取具体验证规则
     *
     * @param cl 注解类型
     * @param i  注解
     * @return 验证信息
     */
    default Validate.ConstraintValidatorField validator(Class<? extends ConstraintValidator<? extends Annotation, ?>> cl, Annotation i) {
        try {
            ConstraintValidator validator = cl.getDeclaredConstructor().newInstance();
            validator.initialize(i);
            return new Validate.ConstraintValidatorField()
                    .setValidator(validator)
                    .setAnnotation(i)
                    .setCode(code(i))
                    .setType(ValidationStrategyType.Annotation)
                    .setMessage(message(i));
        } catch (Exception e) {
            LOGS.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取提示编码
     *
     * @param annotation 注解
     * @return 编码
     */
    default String code(Annotation annotation) {
        try {
            return annotation.getClass().getMethod(CODE).invoke(annotation).toString();
        } catch (Exception ignored) {

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
            return annotation.getClass().getMethod(MESSAGE).invoke(annotation).toString();
        } catch (Exception ignored) {
        }
        return DEFAULT_CODE;
    }
}
