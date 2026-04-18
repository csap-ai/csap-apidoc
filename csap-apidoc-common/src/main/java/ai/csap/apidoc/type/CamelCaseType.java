package ai.csap.apidoc.type;

import java.util.HashMap;
import java.util.Map;

/**
 * 下划线转驼峰
 * 驼峰转下划线
 *
 * @author yangchengfu
 * @dataTime 2019年-12月-27日 17:13:00
 **/
public enum CamelCaseType {
    //默认无
    none,
    //下划线转驼峰
    toCamelCase,
    //驼峰转下划线
    toUnderlineCase;

    private static final Map<String, CamelCaseType> MAPPINGS = new HashMap<>(2);

    static {
        for (CamelCaseType paramType : values()) {
            MAPPINGS.put(paramType.name().toUpperCase(), null);
        }
    }

    public static CamelCaseType resolve(String method) {
        return method != null ? MAPPINGS.get(method.toUpperCase()) : null;
    }

    public boolean matches(String method) {
        return this == resolve(method);
    }
}
