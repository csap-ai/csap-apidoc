package ai.csap.apidoc.runtime.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * 一次响应的过滤状态,只在一次序列化内、单线程使用。
 *
 * <p>path 模式维护一个掩码栈:栈顶是"当前正在输出的对象"适用的掩码。进入一个放行字段的值之前
 * 压入它的子掩码,输出完弹出。被过滤的返回值(控制器方法的返回值)如果被外层统一包装,
 * 包装对象处在 {@link FieldMask#OUTSIDE} 区域,只有遇到返回值本身(按引用判断)时才切换到根掩码。
 *
 * @author ycf
 * @since 1.2.0
 */
public final class FilterSession {

    private final FilterMode mode;

    private final CompiledEndpoint endpoint;

    private final Object original;

    private FieldMask[] stack = new FieldMask[16];

    private int size;

    /**
     * @param mode     模式(PATH 或 CSAP)
     * @param endpoint 接口规则
     * @param original 控制器方法的返回值(被过滤的对象)
     */
    public FilterSession(FilterMode mode, CompiledEndpoint endpoint, Object original) {
        this.mode = mode;
        this.endpoint = endpoint;
        this.original = original;
    }

    public FilterMode getMode() {
        return mode;
    }

    public CompiledEndpoint getEndpoint() {
        return endpoint;
    }

    public Object getOriginal() {
        return original;
    }

    /**
     * 是否 path 模式
     */
    public boolean isPathMode() {
        return mode == FilterMode.PATH;
    }

    /**
     * 开始输出根对象(实际序列化的对象,可能是外层包装)
     *
     * @param root 根对象
     */
    public void begin(Object root) {
        size = 0;
        push(root == original ? endpoint.getPathMask() : FieldMask.OUTSIDE);
    }

    /**
     * 当前对象适用的掩码
     */
    public FieldMask current() {
        return size == 0 ? FieldMask.OUTSIDE : stack[size - 1];
    }

    /**
     * 进入一个值
     */
    public void push(FieldMask mask) {
        if (size == stack.length) {
            stack = Arrays.copyOf(stack, size * 2);
        }
        stack[size++] = mask;
    }

    /**
     * 离开一个值
     */
    public void pop() {
        if (size > 0) {
            stack[--size] = null;
        }
    }

    /**
     * path 模式:当前对象的字段是否输出
     */
    public boolean pathAllows(String name) {
        FieldMask current = current();
        return current == FieldMask.OUTSIDE || current.allows(name);
    }

    /**
     * path 模式:进入当前对象的某个字段的值时应压入的掩码。
     * 当前在外层包装里时,只有值就是被过滤的返回值才进入根掩码。
     *
     * @param name  JSON 键
     * @param value 字段值
     * @return 应压入的掩码;返回 null 表示不需要压栈(下级沿用当前掩码)
     */
    public FieldMask enter(String name, Object value) {
        FieldMask current = current();
        if (current == FieldMask.OUTSIDE) {
            return value != null && value == original ? endpoint.getPathMask() : null;
        }
        if (current == FieldMask.PASS) {
            return null;
        }
        return current.lookup(name);
    }

    /**
     * csap 模式:该对象的字段是否输出
     */
    public boolean typeAllows(Object owner, String name) {
        return owner == null || endpoint.getTypeRules().allows(owner.getClass(), name);
    }

    /**
     * 值是否可能包含需要过滤的下级(对象、集合、数组、Map)。
     * 字符串、数字、日期、枚举等直接输出的值返回 false。
     */
    public static boolean isContainer(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Collection || value instanceof Map) {
            return true;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            return !type.getComponentType().isPrimitive();
        }
        if (type.isEnum() || type.isPrimitive()) {
            return false;
        }
        String name = type.getName();
        return !(name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta."));
    }
}
