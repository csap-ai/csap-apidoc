package ai.csap.apidoc.mcp.web;

import java.lang.reflect.Method;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * 取客户端 IP。通过反射调用 {@code getRemoteAddr()},不产生 javax / jakarta Servlet 的编译期依赖。
 *
 * @author ycf
 * @since 1.3.0
 */
final class RemoteAddress {

    private RemoteAddress() {
    }

    static String current() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        try {
            // getRequest() 在 Spring 5 返回 javax、Spring 6 返回 jakarta 类型,只能反射调用
            Object request = attrs.getClass().getMethod("getRequest").invoke(attrs);
            Method m = request.getClass().getMethod("getRemoteAddr");
            Object v = m.invoke(request);
            return v == null ? null : v.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
