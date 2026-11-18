package ai.csap.apidoc.runtime.filter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;

import ai.csap.apidoc.event.ApidocRulesRefreshedEvent;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.service.ResponseRuleIndex;
import ai.csap.validation.factory.FilterClassParam;
import ai.csap.validation.factory.IValidateBaseFactory;
import ai.csap.validation.factory.Validate;

/**
 * 各接口预编译好的返回过滤规则。
 *
 * <p>读取无锁:规则保存在不可变的 Map 里,刷新时构建新 Map 后整体替换(volatile),
 * 请求线程要么读到旧规则,要么读到新规则,不会读到中间状态。
 * 收到 {@link ApidocRulesRefreshedEvent} 时全量或按类重建。
 *
 * @author ycf
 * @since 1.2.0
 */
public class ResponseRuleRegistry implements ApplicationListener<ApidocRulesRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ResponseRuleRegistry.class);

    private final Predicate<String> hasResponseRules;

    private final Function<String, List<FilterClassParam>> typeRuleSource;

    private volatile Map<String, CompiledEndpoint> endpoints = Collections.emptyMap();

    private volatile boolean ready;

    /**
     * 使用 core 登记的规则
     */
    public ResponseRuleRegistry() {
        this(ResponseRuleIndex::hasResponseRules, ResponseRuleRegistry::filterClassParams);
    }

    /**
     * @param hasResponseRules 接口(类名.方法名)是否配置了返回规则
     * @param typeRuleSource   接口的 csap 模式规则
     */
    public ResponseRuleRegistry(Predicate<String> hasResponseRules,
                                Function<String, List<FilterClassParam>> typeRuleSource) {
        this.hasResponseRules = hasResponseRules;
        this.typeRuleSource = typeRuleSource;
    }

    private static List<FilterClassParam> filterClassParams(String key) {
        Validate validate = IValidateBaseFactory.VALIDATE_MAP.get(key);
        return validate == null ? Collections.emptyList() : validate.getFilterClassParams();
    }

    /**
     * 文档是否已扫描完成(之前所有接口都不过滤)
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * 接口的规则
     *
     * @param key 类名.方法名
     * @return 没有返回规则时为 null
     */
    public CompiledEndpoint get(String key) {
        return endpoints.get(key);
    }

    /**
     * 当前所有有规则的接口(只读快照)
     */
    public Map<String, CompiledEndpoint> snapshot() {
        return endpoints;
    }

    @Override
    public void onApplicationEvent(ApidocRulesRefreshedEvent event) {
        refresh(event.getDocument(), event.getClassNames());
    }

    /**
     * 重建规则
     *
     * @param document   完整文档
     * @param classNames 涉及的类,空集合表示全量
     */
    public synchronized void refresh(CsapDocResponse document, Set<String> classNames) {
        boolean full = classNames == null || classNames.isEmpty();
        Map<String, CompiledEndpoint> next = new HashMap<>();
        if (!full) {
            endpoints.forEach((k, v) -> {
                if (!classNames.contains(v.getClassName())) {
                    next.put(k, v);
                }
            });
        }
        if (document != null && document.getApiList() != null) {
            for (CsapDocModelController controller : document.getApiList()) {
                if (controller == null || controller.getMethodList() == null) {
                    continue;
                }
                if (!full && !classNames.contains(controller.getName())) {
                    continue;
                }
                for (CsapDocMethod method : controller.getMethodList()) {
                    CompiledEndpoint compiled = compile(controller.getName(), method);
                    if (compiled != null) {
                        next.put(compiled.getKey(), compiled);
                    }
                }
            }
        }
        endpoints = Collections.unmodifiableMap(next);
        if (!ready) {
            ready = true;
            log.info("CSAP Apidoc 返回字段过滤已就绪,{} 个接口配置了返回规则", next.size());
        } else if (log.isDebugEnabled()) {
            log.debug("CSAP Apidoc 返回字段过滤规则已刷新({}),{} 个接口配置了返回规则",
                    full ? "全量" : classNames, next.size());
        }
    }

    private CompiledEndpoint compile(String className, CsapDocMethod method) {
        String key = method == null ? null : method.getKey();
        if (key == null || !hasResponseRules.test(key)) {
            return null;
        }
        try {
            return new CompiledEndpoint(key, className, EndpointRuleCompiler.pathMask(method),
                    TypeRules.of(typeRuleSource.apply(key)));
        } catch (RuntimeException e) {
            log.warn("编译接口 {} 的返回过滤规则失败,该接口不过滤: {}", key, e.getMessage(), e);
            return null;
        }
    }
}
