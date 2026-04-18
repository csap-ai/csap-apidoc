package ai.csap.apidoc.annotation;

import java.util.HashMap;
import java.util.Map;

/**
 * API 接口 状态.
 * <p>Created on 2019/12/27
 *
 * @author yangchengfu
 * @since 1.0
 */
public enum ApiStatus {
    //开发阶段
    DEFAULT,
    //完成
    FINISH,
    //过时/过期
    DEPRECATED;

    private static final Map<String, ApiStatus> MAPPINGS = new HashMap<>(16);

    static {
        for (ApiStatus paramType : values()) {
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
    public static ApiStatus resolve(String method) {
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

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
