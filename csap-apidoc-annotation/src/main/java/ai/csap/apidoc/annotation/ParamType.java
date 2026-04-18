package ai.csap.apidoc.annotation;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpMethod;

/**
 * @author yangchengfu
 * @description 参数类型
 * @dataTime 2019年-12月-27日 17:13:00
 **/
public enum ParamType {
    //default 主要是apiOperation 使用
    PATH, QUERY, BODY, FORM_DATA, HEADER, FORM, DEFAULT;

    private static final Map<String, ParamType> MAPPINGS = new HashMap<>(5);
    private static final Map<HttpMethod, ParamType> MAPPINGS2 = new HashMap<>(5);

    static {
        for (ParamType paramType : values()) {
            MAPPINGS.put(paramType.name(), paramType);
        }
        MAPPINGS2.put(HttpMethod.DELETE, ParamType.QUERY);
        MAPPINGS2.put(HttpMethod.GET, ParamType.QUERY);
        MAPPINGS2.put(HttpMethod.PUT, ParamType.BODY);
        MAPPINGS2.put(HttpMethod.POST, ParamType.BODY);
    }

    /**
     * Resolve the given method value to an {@code ParamType}.
     *
     * @param method the method value as a String
     * @return the corresponding {@code ParamType}, or {@code null} if not found
     * @since 4.2.4
     */
    public static ParamType resolve(String method) {
        return method != null ? MAPPINGS.get(method) : null;
    }

    /**
     * Resolve the given method value to an {@code ParamType}.
     *
     * @param method the method value as a String
     * @return the corresponding {@code ParamType}, or {@code null} if not found
     * @since 4.2.4
     */
    public static ParamType resolve2(HttpMethod method) {
        return method != null ? MAPPINGS2.get(method) : null;
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

    /**
     * Determine whether this {@code ParamType} matches the given
     * method value.
     *
     * @param method the method value as a String
     * @return {@code true} if it matches, {@code false} otherwise
     * @since 4.2.4
     */
    public boolean matches2(HttpMethod method) {
        return this == resolve2(method);
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
