package ai.csap.apidoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * try-it-out 全局请求头建议（M7）。
 * <p>
 * 由 scanner 从
 * {@link ai.csap.apidoc.annotation.DocGlobalHeader} 注解收集，序列化在文档 JSON 中
 * 的 {@code globalHeaderHints} 字段，供 {@code csap-apidoc-ui} 自动预填全局头表单。
 * </p>
 *
 * @author yangchengfu
 * @since 1.x M7
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CsapDocGlobalHeaderHint {
    /**
     * 请求头名称，如 {@code X-Tenant-Id}。
     */
    private String name;

    /**
     * 请求头描述。
     */
    private String description;

    /**
     * 请求头示例值，UI 用作 placeholder。
     */
    private String example;

    /**
     * 是否必填。
     */
    private boolean required;
}
