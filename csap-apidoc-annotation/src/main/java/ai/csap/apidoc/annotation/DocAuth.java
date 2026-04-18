package ai.csap.apidoc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明该 Controller / 方法 / 包下接口期望的认证方式，供 try-it-out UI
 * 自动选择对应的鉴权预设。
 * <p>
 * 这是 M7 引入的「devtools 注解提示」之一，仅作为前端 UI（{@code csap-apidoc-ui}）的
 * 推荐预设来源，不会影响后端实际的鉴权处理。
 * </p>
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;DocAuth(scheme = "bearer", description = "由 /auth/login 颁发的 JWT")
 * &#64;DocAuth(scheme = "apikey", in = "header", name = "X-API-Key")
 * </pre>
 *
 * <p>每个目标元素只能声明一个 {@code @DocAuth}（不可重复）。
 * 合并优先级：method &gt; class &gt; package。</p>
 *
 * <p>支持的 {@link #scheme()} 值：</p>
 * <ul>
 *   <li>{@code "bearer"} — Bearer Token（如 JWT）</li>
 *   <li>{@code "basic"} — HTTP Basic 认证</li>
 *   <li>{@code "apikey"} — API Key（配合 {@link #in()} / {@link #name()}）</li>
 *   <li>{@code "oauth2_client"} — OAuth2 Client Credentials</li>
 *   <li>{@code "none"} — 显式声明不需要鉴权</li>
 * </ul>
 * 其他取值会被 scanner 记录 WARN 日志但不抛异常。
 *
 * @author yangchengfu
 * @since 1.x M7
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DocAuth {

    /**
     * 认证方案名称，期望取值之一：
     * {@code "bearer" | "basic" | "apikey" | "oauth2_client" | "none"}。
     */
    String scheme();

    /**
     * 描述信息，用于 UI 展示。
     */
    String description() default "";

    /**
     * 仅对 {@code scheme == "apikey"} 有意义。
     * 取值：{@code "header"}（默认）或 {@code "query"}。
     */
    String in() default "header";

    /**
     * 仅对 {@code scheme == "apikey"} 有意义。
     * 表示 header / query 参数名。
     */
    String name() default "";
}
