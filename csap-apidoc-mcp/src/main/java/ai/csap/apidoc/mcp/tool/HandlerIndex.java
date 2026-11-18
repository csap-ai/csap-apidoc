package ai.csap.apidoc.mcp.tool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Spring MVC 实际注册的接口,按"类名.方法名"(与文档方法 key 一致)索引
 *
 * @author ycf
 * @since 1.3.0
 */
public class HandlerIndex {

    /**
     * 一个处理方法及其映射
     */
    public static class Entry {
        private final HandlerMethod handlerMethod;
        private final List<String> patterns;
        private final Set<String> methods;

        public Entry(HandlerMethod handlerMethod, List<String> patterns, Set<String> methods) {
            this.handlerMethod = handlerMethod;
            this.patterns = patterns;
            this.methods = methods;
        }

        public HandlerMethod getHandlerMethod() {
            return handlerMethod;
        }

        public List<String> getPatterns() {
            return patterns;
        }

        public Set<String> getMethods() {
            return methods;
        }
    }

    private final Map<String, List<Entry>> byKey = new HashMap<>();
    private final int size;

    public HandlerIndex(Collection<RequestMappingHandlerMapping> mappings) {
        int n = 0;
        for (RequestMappingHandlerMapping mapping : mappings) {
            for (Map.Entry<RequestMappingInfo, HandlerMethod> e : mapping.getHandlerMethods().entrySet()) {
                HandlerMethod hm = e.getValue();
                Class<?> beanType = ClassUtils.getUserClass(hm.getBeanType());
                Method m = hm.getMethod();
                Set<String> methods = new LinkedHashSet<>();
                for (RequestMethod rm : e.getKey().getMethodsCondition().getMethods()) {
                    methods.add(rm.name());
                }
                Entry entry = new Entry(hm, patterns(e.getKey()), methods);
                add(beanType.getName() + "." + m.getName(), entry);
                if (!m.getDeclaringClass().equals(beanType)) {
                    add(m.getDeclaringClass().getName() + "." + m.getName(), entry);
                }
                n++;
            }
        }
        this.size = n;
    }

    private void add(String key, Entry entry) {
        byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
    }

    public List<Entry> find(String key) {
        List<Entry> l = byKey.get(key);
        return l == null ? Collections.emptyList() : l;
    }

    public int size() {
        return size;
    }

    /**
     * 映射的 URL 模式。Spring 5.2 / Boot 2 用 getPatternsCondition;
     * Spring 6 默认使用 PathPattern,此时 getPatternsCondition 为 null,改用反射调用 getPathPatternsCondition。
     */
    static List<String> patterns(RequestMappingInfo info) {
        List<String> out = new ArrayList<>();
        if (info.getPatternsCondition() != null) {
            out.addAll(info.getPatternsCondition().getPatterns());
        }
        if (out.isEmpty()) {
            try {
                Object cond = info.getClass().getMethod("getPathPatternsCondition").invoke(info);
                if (cond != null) {
                    Object values = cond.getClass().getMethod("getPatternValues").invoke(cond);
                    if (values instanceof Collection) {
                        for (Object v : (Collection<?>) values) {
                            out.add(String.valueOf(v));
                        }
                    }
                }
            } catch (ReflectiveOperationException ignore) {
                // Spring 5.2 没有该方法
            }
        }
        Collections.sort(out);
        return out;
    }
}
