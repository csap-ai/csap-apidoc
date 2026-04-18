package ai.csap.apidoc.annotation;

import java.util.HashMap;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * 协议.
 * <p>Created on 2019/12/29
 *
 * @author yangchengfu
 * @since 1.0
 */
public enum Protocols {
    HTTP, HTTPS, WS, WSS;

    private static final Map<String, Protocols> MAPPINGS = new HashMap<>(4);

    static {
        for (Protocols protocols : values()) {
            MAPPINGS.put(protocols.name(), protocols);
        }
    }

    /**
     * Resolve the given method value to an {@code ParamType}.
     *
     * @param method the method value as a String
     * @return the corresponding {@code ParamType}, or {@code null} if not found
     * @since 4.2.4
     */
    @Nullable
    public static Protocols resolve(@Nullable String method) {
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
