package ai.csap.apidoc.runtime.filter;

/**
 * 请求属性里暂存的过滤信息:由 {@link ResponseFilterCaptureAdvice} 在其他 ResponseBodyAdvice 之前记录
 * 控制器方法的原始返回值,由 {@link ResponseFilterAdvice} 在最后应用。
 *
 * @author ycf
 * @since 1.2.0
 */
final class PendingFilter {

    static final String ATTRIBUTE = PendingFilter.class.getName();

    private final FilterMode mode;

    private final CompiledEndpoint endpoint;

    private final Object original;

    PendingFilter(FilterMode mode, CompiledEndpoint endpoint, Object original) {
        this.mode = mode;
        this.endpoint = endpoint;
        this.original = original;
    }

    FilterSession newSession() {
        return new FilterSession(mode, endpoint, original);
    }
}
