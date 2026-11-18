package ai.csap.apidoc.runtime.filter;

/**
 * 预编译好的单个接口的返回过滤规则。只为配置了返回规则的接口生成;实例不可变。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class CompiledEndpoint {

    private final String key;

    private final String className;

    private final FieldMask pathMask;

    private final TypeRules typeRules;

    /**
     * @param key       类名.方法名
     * @param className 类名
     * @param pathMask  path 模式的掩码,为 null 表示 path 模式下不过滤
     * @param typeRules csap 模式的规则
     */
    public CompiledEndpoint(String key, String className, FieldMask pathMask, TypeRules typeRules) {
        this.key = key;
        this.className = className;
        this.pathMask = pathMask;
        this.typeRules = typeRules == null ? TypeRules.EMPTY : typeRules;
    }

    public String getKey() {
        return key;
    }

    public String getClassName() {
        return className;
    }

    /**
     * path 模式的掩码;为 null 表示 path 模式下不过滤(如返回基础类型)
     */
    public FieldMask getPathMask() {
        return pathMask;
    }

    /**
     * csap 模式的规则
     */
    public TypeRules getTypeRules() {
        return typeRules;
    }

    /**
     * 该模式下是否需要过滤
     */
    public boolean filters(FilterMode mode) {
        switch (mode) {
            case PATH:
                return pathMask != null;
            case CSAP:
                return !typeRules.isEmpty();
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return key + " path=" + pathMask + " csap=" + typeRules;
    }
}
