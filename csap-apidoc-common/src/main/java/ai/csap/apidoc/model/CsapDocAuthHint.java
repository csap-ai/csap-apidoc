package ai.csap.apidoc.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * try-it-out 认证方式建议（M7）。
 * <p>
 * 由 scanner 从 {@link ai.csap.apidoc.annotation.DocAuth} 注解收集，序列化在文档
 * JSON 中的 {@code authHint} 字段。{@code in} 与 {@code name} 仅在
 * {@code scheme == "apikey"} 时有效，其他场景 scanner 会留空，Jackson 在
 * {@link JsonInclude.Include#NON_EMPTY} 策略下自动省略。
 * </p>
 *
 * @author yangchengfu
 * @since 1.x M7
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CsapDocAuthHint {
    /**
     * 认证方案，如 {@code bearer / basic / apikey / oauth2_client / none}。
     */
    private String scheme;

    /**
     * 描述信息。
     */
    private String description;

    /**
     * 仅 {@code apikey} 有意义：{@code header} / {@code query}。
     */
    private String in;

    /**
     * 仅 {@code apikey} 有意义：header / query 参数名。
     */
    private String name;
}
