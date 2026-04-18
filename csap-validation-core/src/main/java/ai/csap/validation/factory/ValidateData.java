package ai.csap.validation.factory;

import static ai.csap.apidoc.util.ApidocClazzUtils.OBJ;

import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.core.MethodParameter;

import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.annotation.ApiPropertys;

/**
 * 数据验证接口.
 * <p>Created on 2021/1/24
 *
 * @author yangchengfu
 * @since 1.0
 */
public interface ValidateData {

    /**
     * 单参数使用 是否验证属性
     *
     * @param method    当前方法
     * @param fieldName 属性名称
     * @return 是否需要验证
     */
    default boolean isPropertieValidate(MethodParameter method, final String fieldName) {
        if (method.hasMethodAnnotation(ApiProperty.class)) {
            return Stream.of(method.getMethodAnnotation(ApiProperty.class)).filter(Objects::nonNull).anyMatch(i -> i.name().equals(fieldName) && i.required());
        } else if (method.hasMethodAnnotation(ApiPropertys.class)) {
            return Stream.of(Objects.requireNonNull(method.getMethodAnnotation(ApiPropertys.class)).value()).anyMatch(i -> i.name().equals(fieldName) && i.required());
        }
        return false;
    }

    /**
     * 过滤掉不需要验证的参数
     *
     * @param name 类型名称
     * @return
     */
    default boolean getClObj(String name) {
        boolean validate = true;
        if (OBJ.contains(name)) {
            validate = false;
        }
        return validate;
    }

}
