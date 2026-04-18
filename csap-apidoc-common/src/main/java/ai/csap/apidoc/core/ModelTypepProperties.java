package ai.csap.apidoc.core;

import java.lang.reflect.Type;
import java.util.List;

import ai.csap.apidoc.type.ModelType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description Bean的类型
 * T_OBJECT 对象泛型、
 * OBJECT 对象
 * ARRAYS list泛型
 * @dataTime 2020年-01月-03日 19:37:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ModelTypepProperties {
    /**
     * 参数类型
     */
    private ModelType modelType;
    /**
     * 具体对象「泛型的对象，具体对象」!具体对象
     */
    private Class<?> cl;
    /**
     * 具体对象「泛型的对象，具体对象」!具体对象 list
     */
    private List<Type> clList;
    /**
     * 泛型
     */
    private Type type;
    /**
     * 真实对象
     */
    private Class<?> rawCl;
    /**
     * 真实的对象 class 路径
     */
    private String rawType;

    /**
     * 拼接的字符串，包括泛型拼接，普通拼接
     */
    private String subStr;

    /**
     * 是否泛型
     */
    private boolean t;

    public String getSubStr() {
        return subStr;
    }
}
