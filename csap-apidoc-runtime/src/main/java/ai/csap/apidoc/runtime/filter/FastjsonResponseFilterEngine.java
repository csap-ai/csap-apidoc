package ai.csap.apidoc.runtime.filter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.JSONSerializable;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.PropertyPreFilter;
import com.alibaba.fastjson.serializer.ValueFilter;
import com.alibaba.fastjson.support.spring.FastJsonContainer;
import com.alibaba.fastjson.support.spring.MappingFastJsonValue;

/**
 * fastjson 1.x 引擎(配合 {@code com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter})。
 *
 * <p>把响应体包装成 {@link JSONSerializable},写出时临时给本次的 JSONSerializer 加上过滤器:
 * <ul>
 *   <li>{@link PropertyPreFilter} 决定字段是否输出;</li>
 *   <li>path 模式下 {@link ValueFilter} 把放行字段的值包一层 {@link Scoped},写出该值期间压入它的子掩码,
 *       不依赖 fastjson 的 SerialContext(开启 DisableCircularReferenceDetect 也能用)。</li>
 * </ul>
 *
 * @author ycf
 * @since 1.2.0
 */
public class FastjsonResponseFilterEngine implements ResponseFilterEngine {

    private static final String CONVERTER_PACKAGE = "com.alibaba.fastjson.support.spring.";

    /**
     * fastjson2 的兼容包也提供 com.alibaba.fastjson 包名,但实现不同,不在这里处理
     */
    static final boolean FASTJSON_1 = isFastjson1();

    /**
     * JSON.VERSION 是编译期常量,必须反射读取运行时的值
     */
    private static boolean isFastjson1() {
        try {
            Object version = JSON.class.getField("VERSION").get(null);
            return version instanceof String && ((String) version).startsWith("1.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public FilterEngine type() {
        return FilterEngine.FASTJSON;
    }

    @Override
    public boolean supports(Class<?> converterType) {
        if (!FASTJSON_1) {
            return false;
        }
        for (Class<?> c = converterType; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().startsWith(CONVERTER_PACKAGE) && c.getSimpleName().startsWith("FastJsonHttpMessageConverter")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object apply(Object body, FilterSession session, Class<?> converterType) {
        if (body instanceof MappingFastJsonValue) {
            MappingFastJsonValue value = (MappingFastJsonValue) body;
            value.setValue(new Root(value.getValue(), session));
            return value;
        }
        if (body instanceof FastJsonContainer) {
            FastJsonContainer container = (FastJsonContainer) body;
            container.setValue(new Root(container.getValue(), session));
            return container;
        }
        return new Root(body, session);
    }

    /**
     * 响应体:写出时加上过滤器。path 模式下条件允许时(见 {@link FastjsonFastPath#eligible})被过滤的值走快速路径
     */
    static final class Root implements JSONSerializable {
        private final Object value;
        private final FilterSession session;

        Root(Object value, FilterSession session) {
            this.value = value;
            this.session = session;
        }

        @Override
        public void write(JSONSerializer serializer, Object fieldName, Type fieldType, int features)
                throws IOException {
            if (value == null) {
                serializer.writeNull();
                return;
            }
            boolean fast = FastjsonFastPath.eligible(serializer);
            if (fast && !session.isPathMode()) {
                FastjsonFastPath.writeCsap(serializer, value, session.getEndpoint().getTypeRules(), session);
                return;
            }
            if (fast && value == session.getOriginal()) {
                session.begin(value);
                try {
                    FastjsonFastPath.writeRoot(serializer, value, session.current(), session);
                } finally {
                    session.pop();
                }
                return;
            }
            Filters filters = new Filters(session, fast);
            List<PropertyPreFilter> preFilters = serializer.getPropertyPreFilters();
            preFilters.add(filters);
            List<ValueFilter> valueFilters = session.isPathMode() ? serializer.getValueFilters() : null;
            if (valueFilters != null) {
                valueFilters.add(filters);
            }
            session.begin(value);
            try {
                serializer.write(value);
            } finally {
                session.pop();
                preFilters.remove(filters);
                if (valueFilters != null) {
                    valueFilters.remove(filters);
                }
            }
        }
    }

    /**
     * 放行字段的值:写出期间压入子掩码
     */
    static final class Scoped implements JSONSerializable {
        private final Object value;
        private final FieldMask mask;
        private final Filters filters;

        Scoped(Object value, FieldMask mask, Filters filters) {
            this.value = value;
            this.mask = mask;
            this.filters = filters;
        }

        @Override
        public void write(JSONSerializer serializer, Object fieldName, Type fieldType, int features)
                throws IOException {
            FilterSession session = filters.session;
            if (filters.fast) {
                // 外层包装里遇到被过滤的值:暂时摘掉全局过滤器,走快速路径
                serializer.getPropertyPreFilters().remove(filters);
                serializer.getValueFilters().remove(filters);
                try {
                    FastjsonFastPath.writeRoot(serializer, value, mask, session);
                } finally {
                    serializer.getPropertyPreFilters().add(filters);
                    serializer.getValueFilters().add(filters);
                }
                return;
            }
            session.push(mask);
            try {
                serializer.write(value);
            } finally {
                session.pop();
            }
        }
    }

    static final class Filters implements PropertyPreFilter, ValueFilter {
        private final FilterSession session;
        private final boolean fast;

        /**
         * @param session 过滤状态
         * @param fast    放行的容器值是否用快速路径写出
         */
        Filters(FilterSession session, boolean fast) {
            this.session = session;
            this.fast = fast;
        }

        @Override
        public boolean apply(JSONSerializer serializer, Object object, String name) {
            if (session.isPathMode()) {
                return session.pathAllows(name);
            }
            return session.typeAllows(object, name);
        }

        @Override
        public Object process(Object object, String name, Object value) {
            if (!FilterSession.isContainer(value)) {
                return value;
            }
            FieldMask next = session.enter(name, value);
            if (next == null || next == FieldMask.DROP) {
                return value;
            }
            return new Scoped(value, next, this);
        }
    }
}
