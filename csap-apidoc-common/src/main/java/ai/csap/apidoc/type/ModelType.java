package ai.csap.apidoc.type;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yangchengfu
 * @description 方法参数类型
 * @dataTime 2019年-12月-27日 17:13:00
 **/
public enum ModelType {
    //对象类型
    OBJECT,
    //数组类型
    ARRAY,
    //对象泛型
    T_OBJECT,
    //基本数据类型
    BASE_DATA;

    private static final Map<String, ModelType> MAPPINGS = new HashMap<>(2);

    static {
        for (ModelType paramType : values()) {
            MAPPINGS.put(paramType.name(), null);
        }
    }

    /**
     * Resolve the given method value to an {@code ParamType}.
     *
     * @param method the method value as a String
     * @return the corresponding {@code ParamType}, or {@code null} if not found
     * @since 4.2.4
     */
    public static ModelType resolve(String method) {
        return method != null ? MAPPINGS.get(method) : null;
    }

    /**
     * Determine whether this {@code ParamType} matches the given
     * method value.
     *
     * @param method the method value as a String
     * @return {@code true} if it matches, {@code false} otherwise
     * @since 4.2.4
     */
    public boolean matches(String method) {
        return this == resolve(method);
    }
}
