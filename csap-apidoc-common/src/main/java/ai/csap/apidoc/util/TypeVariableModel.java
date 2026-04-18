package ai.csap.apidoc.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;

import lombok.Data;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2020年-03月-11日 11:19:00
 **/
@Data
public final class TypeVariableModel {
    /**
     * 父类泛型实际数据
     */
    private List<TypeVariable> typeVariables;
    /**
     * 泛型
     */
    private ParameterizedType parameterizedType;
    /**
     * 子类真实类型
     */
    private List<Type> rawType;
    /**
     * 是否存在父类泛型方法
     */
    private boolean typeVariable;
    /**
     * 当前类型
     */
    private Class aClass;

    public TypeVariableModel(Class cl) {
        init(cl);
    }

    private TypeVariableModel init(Class cl) {
        if (cl.getGenericSuperclass() != null && cl.getGenericSuperclass() instanceof ParameterizedType) {
            setParameterizedType((ParameterizedType) cl.getGenericSuperclass());
            setTypeVariables(Arrays.asList(((Class) getParameterizedType().getRawType()).getTypeParameters()));
            setRawType(Arrays.asList(getParameterizedType().getActualTypeArguments()));
            setTypeVariable(true);
        }
        aClass = cl;
        return this;
    }
}
