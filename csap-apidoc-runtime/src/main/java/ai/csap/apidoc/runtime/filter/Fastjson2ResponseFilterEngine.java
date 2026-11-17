package ai.csap.apidoc.runtime.filter;

import java.lang.reflect.Type;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONType;
import com.alibaba.fastjson2.filter.PropertyPreFilter;
import com.alibaba.fastjson2.filter.ValueFilter;
import com.alibaba.fastjson2.writer.ObjectWriter;

/**
 * fastjson2 引擎(配合 fastjson2-extension-spring5 / spring6 的 {@code FastJsonHttpMessageConverter})。
 *
 * <p>把响应体包装成自带 ObjectWriter 的对象,写出时临时替换 JSONWriter.Context 上的
 * PropertyPreFilter / ValueFilter(与原有过滤器组合),写完恢复。path 模式的掩码栈做法同 fastjson 1。
 *
 * @author ycf
 * @since 1.2.0
 */
public class Fastjson2ResponseFilterEngine implements ResponseFilterEngine {

    @Override
    public FilterEngine type() {
        return FilterEngine.FASTJSON2;
    }

    @Override
    public boolean supports(Class<?> converterType) {
        for (Class<?> c = converterType; c != null && c != Object.class; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.startsWith("com.alibaba.fastjson2.") && name.endsWith("FastJsonHttpMessageConverter")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object apply(Object body, FilterSession session, Class<?> converterType) {
        return new Root(body, session);
    }

    private static void writeValue(JSONWriter writer, Object value, Object fieldName, long features) {
        if (value == null) {
            writer.writeNull();
            return;
        }
        writer.getObjectWriter(value.getClass()).write(writer, value, fieldName, null, features);
    }

    /**
     * 响应体
     */
    @JSONType(serializer = RootWriter.class)
    static final class Root {
        private final Object value;
        private final FilterSession session;

        Root(Object value, FilterSession session) {
            this.value = value;
            this.session = session;
        }
    }

    /**
     * 放行字段的值:写出期间压入子掩码
     */
    @JSONType(serializer = ScopedWriter.class)
    static final class Scoped {
        private final Object value;
        private final FieldMask mask;
        private final FilterSession session;

        Scoped(Object value, FieldMask mask, FilterSession session) {
            this.value = value;
            this.mask = mask;
            this.session = session;
        }
    }

    /**
     * {@link Root} 的写出:组合过滤器、写出、恢复
     */
    public static final class RootWriter implements ObjectWriter<Object> {
        @Override
        public void write(JSONWriter writer, Object object, Object fieldName, Type fieldType, long features) {
            Root root = (Root) object;
            if (root.value == null) {
                writer.writeNull();
                return;
            }
            FilterSession session = root.session;
            JSONWriter.Context context = writer.getContext();
            PropertyPreFilter oldPre = context.getPropertyPreFilter();
            ValueFilter oldValue = context.getValueFilter();
            context.setPropertyPreFilter((w, source, name) -> {
                boolean keep = session.isPathMode() ? session.pathAllows(name) : session.typeAllows(source, name);
                return keep && (oldPre == null || oldPre.process(w, source, name));
            });
            if (session.isPathMode()) {
                context.setValueFilter((owner, name, value) -> {
                    Object v = oldValue == null ? value : oldValue.apply(owner, name, value);
                    if (!FilterSession.isContainer(v)) {
                        return v;
                    }
                    FieldMask next = session.enter(name, v);
                    return next == null || next == FieldMask.DROP ? v : new Scoped(v, next, session);
                });
            }
            session.begin(root.value);
            try {
                writeValue(writer, root.value, fieldName, features);
            } finally {
                session.pop();
                context.setPropertyPreFilter(oldPre);
                context.setValueFilter(oldValue);
            }
        }
    }

    /**
     * {@link Scoped} 的写出
     */
    public static final class ScopedWriter implements ObjectWriter<Object> {
        @Override
        public void write(JSONWriter writer, Object object, Object fieldName, Type fieldType, long features) {
            Scoped scoped = (Scoped) object;
            scoped.session.push(scoped.mask);
            try {
                writeValue(writer, scoped.value, fieldName, features);
            } finally {
                scoped.session.pop();
            }
        }
    }
}
