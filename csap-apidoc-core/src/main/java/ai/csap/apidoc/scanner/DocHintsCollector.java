package ai.csap.apidoc.scanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.csap.apidoc.annotation.DocAuth;
import ai.csap.apidoc.annotation.DocGlobalHeader;
import ai.csap.apidoc.model.CsapDocAuthHint;
import ai.csap.apidoc.model.CsapDocGlobalHeaderHint;

/**
 * 从 {@link DocGlobalHeader} / {@link DocAuth} 注解收集 try-it-out 提示信息。
 * <p>
 * 合并优先级一致：method &gt; class &gt; package。提取为独立工具类便于单元测试，
 * 避免与重型 scanner ({@code ApiDocService}) 耦合。
 * </p>
 *
 * <h3>多源策略说明（重要）</h3>
 * <p>
 * 本类仅服务 <b>{@code docType=annotation}</b> 路径。{@code globalHeaderHints} /
 * {@code authHint} 字段定义在 {@link ai.csap.apidoc.model.CsapDocMethod}，
 * 因此其他文档来源同样可以填充：
 * </p>
 * <ul>
 *   <li><b>YAML（{@code docType=yaml}，{@code YamlApidocStrategy}）：</b>
 *       零代码改造。{@code YAMLMapper} 配置了
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 并直接反序列化为
 *       {@code CsapDocMethod}，所以在 {@code application-method.yaml} 的方法节点下
 *       新增 {@code globalHeaderHints} / {@code authHint} 字段即可，
 *       字段语义与本类输出完全一致。
 *       样例见 {@code csap-apidoc-strategy/csap-apidoc-yaml/src/test/resources/
 *       application-hints-method.yaml}。</li>
 *   <li><b>SQLite（{@code docType=sql_lite}，{@code SqliteApidocStrategy}）：</b>
 *       当前 {@code load()} 不读 hint 字段，需要为
 *       {@code api_method} 表新增 {@code global_header_hints_json} /
 *       {@code auth_hint_json} 列并扩展 loader。计划在 M7.1 follow-up PR 落地。</li>
 * </ul>
 *
 * @author yangchengfu
 * @since 1.x M7
 */
public final class DocHintsCollector {

    private static final Logger LOG = LoggerFactory.getLogger(DocHintsCollector.class);

    /**
     * {@link DocAuth#scheme()} 允许取值。
     */
    private static final Set<String> SUPPORTED_SCHEMES = new HashSet<>(
            Arrays.asList("bearer", "basic", "apikey", "oauth2_client", "none"));

    private DocHintsCollector() {
    }

    /**
     * 收集指定方法的全局请求头建议。
     * 三层来源按 method &gt; class &gt; package 顺序合并，相同 {@code name} 时高优先级覆盖。
     *
     * @param controllerClass Controller 类（{@code null} 时仅扫描 method / 无 method 时返回空列表）
     * @param method          方法（可为 {@code null}，此时只扫 class+package）
     * @return 去重后的全局头建议列表，永远非空
     */
    public static List<CsapDocGlobalHeaderHint> collectGlobalHeaderHints(Class<?> controllerClass, Method method) {
        // LinkedHashMap 保留插入顺序，便于 UI 稳定渲染
        Map<String, CsapDocGlobalHeaderHint> merged = new LinkedHashMap<>();

        // 优先级低 -> 高：package -> class -> method，后写入覆盖前者
        if (controllerClass != null && controllerClass.getPackage() != null) {
            for (DocGlobalHeader h : findGlobalHeaders(controllerClass.getPackage())) {
                merged.put(h.name(), toHint(h));
            }
        }
        if (controllerClass != null) {
            for (DocGlobalHeader h : findGlobalHeaders(controllerClass)) {
                merged.put(h.name(), toHint(h));
            }
        }
        if (method != null) {
            for (DocGlobalHeader h : findGlobalHeaders(method)) {
                merged.put(h.name(), toHint(h));
            }
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * 解析方法上生效的认证建议。method &gt; class &gt; package 取首个非空值。
     *
     * @param controllerClass Controller 类
     * @param method          方法
     * @return 命中的认证建议，若三层均无声明返回 {@code null}
     */
    public static CsapDocAuthHint resolveAuthHint(Class<?> controllerClass, Method method) {
        DocAuth a = null;
        if (method != null) {
            a = method.getAnnotation(DocAuth.class);
        }
        if (a == null && controllerClass != null) {
            a = controllerClass.getAnnotation(DocAuth.class);
        }
        if (a == null && controllerClass != null && controllerClass.getPackage() != null) {
            a = controllerClass.getPackage().getAnnotation(DocAuth.class);
        }
        return a == null ? null : toHint(a, controllerClass, method);
    }

    private static DocGlobalHeader[] findGlobalHeaders(java.lang.reflect.AnnotatedElement element) {
        // 直接读 List 容器最稳妥（避免 getAnnotationsByType 在某些 JDK 上的边界差异）
        DocGlobalHeader.List container = element.getAnnotation(DocGlobalHeader.List.class);
        if (container != null) {
            return container.value();
        }
        DocGlobalHeader single = element.getAnnotation(DocGlobalHeader.class);
        if (single != null) {
            return new DocGlobalHeader[]{single};
        }
        return new DocGlobalHeader[0];
    }

    private static CsapDocGlobalHeaderHint toHint(DocGlobalHeader h) {
        return CsapDocGlobalHeaderHint.builder()
                .name(h.name())
                .description(h.description())
                .example(h.example())
                .required(h.required())
                .build();
    }

    private static CsapDocAuthHint toHint(DocAuth a, Class<?> controllerClass, Method method) {
        String scheme = a.scheme();
        if (!SUPPORTED_SCHEMES.contains(scheme)) {
            // WARN 但不抛错——保持向前兼容，未来可能扩展更多 scheme
            LOG.warn(
                    "Unrecognised @DocAuth scheme '{}' on {}; expected one of {}. Hint will still be exposed verbatim.",
                    scheme, describe(controllerClass, method), SUPPORTED_SCHEMES);
        }
        boolean isApiKey = "apikey".equals(scheme);
        return CsapDocAuthHint.builder()
                .scheme(scheme)
                .description(a.description())
                .in(isApiKey ? a.in() : null)
                .name(isApiKey ? a.name() : null)
                .build();
    }

    private static String describe(Class<?> controllerClass, Method method) {
        if (controllerClass == null && method == null) {
            return "<unknown>";
        }
        if (method != null) {
            String owner = method.getDeclaringClass().getName();
            return owner + "#" + method.getName();
        }
        return controllerClass.getName();
    }

    /**
     * 仅供测试。返回当前接受的 scheme 集合（不可变）。
     */
    public static Set<String> supportedSchemes() {
        return Collections.unmodifiableSet(SUPPORTED_SCHEMES);
    }
}
