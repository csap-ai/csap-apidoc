package ai.csap.apidoc.event;

import java.util.Collections;
import java.util.Set;

import org.springframework.context.ApplicationEvent;

import ai.csap.apidoc.model.CsapDocResponse;

/**
 * 文档与字段规则重新生成完成的事件。
 *
 * <p>{@code ApidocContext} 在全量扫描({@code cmd(flush, ...)})或按类刷新({@code cmd(className, ...)})
 * 完成后,在扫描锁内同步发布本事件。监听方(如运行时返回字段过滤)据此重建自己的规则缓存;
 * 由于在锁内同步发布,监听方处理期间不会有另一轮扫描修改规则。
 *
 * @author ycf
 * @since 1.2.0
 */
public class ApidocRulesRefreshedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final transient CsapDocResponse document;

    private final Set<String> classNames;

    /**
     * @param document   刷新后的完整文档
     * @param classNames 本次刷新涉及的类名;为空表示全量刷新
     */
    public ApidocRulesRefreshedEvent(CsapDocResponse document, Set<String> classNames) {
        super(document);
        this.document = document;
        this.classNames = classNames == null ? Collections.emptySet() : Collections.unmodifiableSet(classNames);
    }

    /**
     * 刷新后的完整文档。
     */
    public CsapDocResponse getDocument() {
        return document;
    }

    /**
     * 本次刷新涉及的类名;为空表示全量刷新。
     */
    public Set<String> getClassNames() {
        return classNames;
    }

    /**
     * 是否全量刷新。
     */
    public boolean isFullRefresh() {
        return classNames.isEmpty();
    }
}
