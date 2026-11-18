package ai.csap.apidoc.devtools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.model.RuleChange;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.strategy.ParamGroupStrategy;
import ai.csap.apidoc.strategy.RuleHistoryProvider;
import ai.csap.apidoc.util.RuleStorageNotWritableException;

/**
 * 规则修改记录与回滚(只在 {@code paramType} 为 {@code DATABASE} 或 {@code REDIS} 时可用)。
 *
 * <p>其他存储模式下接口返回失败结果和说明,不报 500。回滚在只读模式下返回 {@code code = 1007}。
 * 回滚成功后,策略会重新加载规则并重新生成受影响类的文档和字段规则。
 *
 * @author ycf
 * @since 1.4.0
 */
@RequestMapping("devtools/rules")
@RestController
public class ApidocRuleHistoryController {

    /**
     * 回滚时记录的修改人
     */
    public static final String ROLLBACK_BY = "devtools-rollback";

    /**
     * 其他存储模式的提示
     */
    static final String NOT_SUPPORTED = "修改记录与回滚只在数据库模式或 Redis 模式下可用:"
            + "@EnableApidoc(paramType = ApiStrategyType.DATABASE) 或 ApiStrategyType.REDIS。"
            + " | Change history and rollback are only available in the database or Redis mode: "
            + "@EnableApidoc(paramType = ApiStrategyType.DATABASE) or ApiStrategyType.REDIS.";

    private final EnableApidocConfig enableApidocConfig;

    public ApidocRuleHistoryController(EnableApidocConfig enableApidocConfig) {
        this.enableApidocConfig = enableApidocConfig;
    }

    private static boolean externalStore(ApiStrategyType t) {
        return t == ApiStrategyType.DATABASE || t == ApiStrategyType.REDIS;
    }

    /**
     * 当前使用的外部规则存储类型(数据库 / Redis),不是这两种时返回 null
     */
    private ApiStrategyType storeType() {
        if (enableApidocConfig == null) {
            return null;
        }
        if (externalStore(enableApidocConfig.getParamType())) {
            return enableApidocConfig.getParamType();
        }
        List<StrategyModel> list = enableApidocConfig.getApiPackageClass();
        if (list != null) {
            for (StrategyModel m : list) {
                if (externalStore(m.getParamType())) {
                    return m.getParamType();
                }
            }
        }
        return null;
    }

    private RuleHistoryProvider provider() {
        ApiStrategyType type = storeType();
        if (type == null) {
            return null;
        }
        ParamGroupStrategy strategy = ApidocContext.paramStrategy(type);
        return strategy instanceof RuleHistoryProvider ? (RuleHistoryProvider) strategy : null;
    }

    /**
     * 当前是否支持修改记录 / 回滚,以及是否只读
     */
    @GetMapping("status")
    public ApidocResult<Map<String, Object>> status() {
        RuleHistoryProvider p = provider();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("supported", p != null);
        m.put("readOnly", p != null && p.isReadOnly());
        m.put("paramType", enableApidocConfig == null || enableApidocConfig.getParamType() == null
                ? null : enableApidocConfig.getParamType().getName());
        if (p == null) {
            // 语言中立的前端优先显示后端给的 hint(Java 写法);格式见 docs/design/devtools-hints.md
            Map<String, String> hint = new LinkedHashMap<>();
            hint.put("zh", "把启动类上的 @EnableApidoc 改为(二选一):\n@EnableApidoc(paramType = ApiStrategyType.DATABASE)\n@EnableApidoc(paramType = ApiStrategyType.REDIS)");
            hint.put("en", "Change @EnableApidoc to (either):\n@EnableApidoc(paramType = ApiStrategyType.DATABASE)\n@EnableApidoc(paramType = ApiStrategyType.REDIS)");
            m.put("hint", hint);
        }
        return ApidocResult.success(m);
    }

    /**
     * 修改记录,最新的在前
     *
     * @param ruleKey 规则键(类全名.方法名),为空返回全部
     * @param limit   条数,默认 100
     */
    @GetMapping("history")
    public ApidocResult<List<RuleChange>> history(@RequestParam(required = false) String ruleKey,
                                                  @RequestParam(required = false, defaultValue = "100") int limit) {
        RuleHistoryProvider p = provider();
        if (p == null) {
            return ApidocResult.error("400", NOT_SUPPORTED);
        }
        return ApidocResult.success(p.history(ruleKey, Math.min(Math.max(limit, 1), 1000)));
    }

    /**
     * 回滚到某条修改记录之前的内容
     */
    @PostMapping("rollback")
    public ApidocResult<Map<String, Object>> rollback(@RequestBody Map<String, Object> body) {
        RuleHistoryProvider p = provider();
        if (p == null) {
            return ApidocResult.error("400", NOT_SUPPORTED);
        }
        Object id = body == null ? null : body.get("changeId");
        if (!(id instanceof Number) && (id == null || !id.toString().matches("\\d+"))) {
            return ApidocResult.error("400", "缺少 changeId(修改记录 id)。 | changeId is required.");
        }
        long changeId = id instanceof Number ? ((Number) id).longValue() : Long.parseLong(id.toString());
        long version;
        try {
            version = p.rollback(changeId, ROLLBACK_BY);
        } catch (IllegalArgumentException e) {
            return ApidocResult.error("400", Objects.toString(e.getMessage(), "修改记录不存在"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rolledBack", version >= 0);
        m.put("version", version >= 0 ? version : null);
        return ApidocResult.success(m);
    }

    @ExceptionHandler(RuleStorageNotWritableException.class)
    public ApidocResult<Object> ruleStorageNotWritable(RuleStorageNotWritableException e) {
        return ApidocResult.error(e.getCode(), e.getMessage(), e.getData());
    }
}
