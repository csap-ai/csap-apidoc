package ai.csap.apidoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明该 Controller / 方法 / 包下的接口在 try-it-out 时建议附带的全局 HTTP 请求头。
 * <p>
 * 这是 M7 引入的「devtools 注解提示」之一，仅作为前端 UI（{@code csap-apidoc-ui}）的
 * 推荐预设来源；不会影响实际请求处理逻辑，也不强制调用方必须携带这些头。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;DocGlobalHeader(name = "X-Tenant-Id", description = "租户标识", example = "demo", required = true)
 * &#64;DocGlobalHeader(name = "X-Trace-Id", description = "可选客户端 trace id", required = false)
 * &#64;RestController
 * public class TenantController { ... }
 * </pre>
 *
 * <p>支持作用范围：</p>
 * <ul>
 *   <li>{@link ElementType#TYPE} — Controller 类</li>
 *   <li>{@link ElementType#METHOD} — 单个接口方法</li>
 *   <li>{@link ElementType#PACKAGE} — 整个包（{@code package-info.java}）</li>
 * </ul>
 *
 * <p>合并优先级（method &gt; class &gt; package），相同 {@code name} 时高优先级覆盖。</p>
 *
 * @author yangchengfu
 * @since 1.x M7
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DocGlobalHeader.List.class)
public @interface DocGlobalHeader {

    /**
     * 请求头名称（必填），如 {@code "X-Tenant-Id"}。
     */
    String name();

    /**
     * 请求头描述，展示在 UI 的 tooltip 中。
     */
    String description() default "";

    /**
     * 请求头示例值，UI 会用作输入框的 placeholder / 默认值。
     */
    String example() default "";

    /**
     * 是否必填。{@code true} 时 UI 会高亮提示。
     */
    boolean required() default false;

    /**
     * 容器注解，配合 {@link Repeatable} 实现可重复使用。
     * 通常无需直接使用，编译器会自动包装多个 {@link DocGlobalHeader}。
     */
    @Target({ElementType.TYPE, ElementType.METHOD, ElementType.PACKAGE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        DocGlobalHeader[] value();
    }
}
