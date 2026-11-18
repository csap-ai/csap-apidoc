package ai.csap.apidoc.database;

import static ai.csap.apidoc.util.IValidate.DOT;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.model.RuleChange;
import ai.csap.apidoc.strategy.ParamGroupStrategy;
import ai.csap.apidoc.strategy.RuleHistoryProvider;
import ai.csap.apidoc.util.ApidocUtils;
import ai.csap.apidoc.util.RuleStorageNotWritableException;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;

import cn.hutool.core.collection.CollectionUtil;

/**
 * 数据库参数分组模式({@code paramType = ApiStrategyType.DATABASE}):字段规则存在应用自己的关系数据库里。
 *
 * <ul>
 *   <li>首次使用时建表(可关闭)并把本服务的全部规则加载到内存,查找都走内存;</li>
 *   <li>保存时把该接口的 request + response 整体写成一行,同时记录修改记录;</li>
 *   <li>后台守护线程按 poll-interval 轮询版本号,别的实例保存后本实例自动重新加载;</li>
 *   <li>只读模式下只读取,保存会被拒绝。</li>
 * </ul>
 *
 * @author ycf
 * @since 1.4.0
 */
public class DatabaseParamGroupStrategy implements ParamGroupStrategy, RuleHistoryProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseParamGroupStrategy.class);

    /**
     * 保存规则时记录的修改人
     */
    static final String UPDATED_BY = "devtools";

    private final IValidateFactory validateFactory;
    private final Supplier<DatabaseRuleStore> storeSupplier;
    private final boolean initSchema;
    private final boolean readOnly;
    private final Duration pollInterval;

    private final Object lock = new Object();
    private volatile boolean initialized;
    private volatile boolean closed;
    private volatile DatabaseRuleStore store;
    private volatile Map<String, Map<String, ParamGroupMethodProperty>> rules = new ConcurrentHashMap<>(16);
    private volatile long loadedVersion = Long.MIN_VALUE;
    private ScheduledExecutorService poller;
    private volatile boolean pollFailing;
    private volatile Consumer<Set<String>> reloadListener;

    /**
     * @param validateFactory 注解校验器工厂,可为 null
     * @param storeSupplier   规则表访问对象;第一次用到数据库时才调用(此时才解析 DataSource)
     * @param initSchema      表不存在时是否自动建表
     * @param readOnly        只读模式
     * @param pollInterval    轮询版本号的间隔,null 或 0 表示不轮询
     */
    public DatabaseParamGroupStrategy(IValidateFactory validateFactory, Supplier<DatabaseRuleStore> storeSupplier,
                                      boolean initSchema, boolean readOnly, Duration pollInterval) {
        this.validateFactory = validateFactory;
        this.storeSupplier = Objects.requireNonNull(storeSupplier, "storeSupplier");
        this.initSchema = initSchema;
        this.readOnly = readOnly;
        this.pollInterval = pollInterval;
    }

    @Override
    public String getName() {
        return ApiStrategyType.DATABASE.getName();
    }

    @Override
    public String getSuffix() {
        return ApiStrategyType.DATABASE.getSuffix();
    }

    @Override
    public ApidocStrategyName strategyType() {
        return this;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * 规则存储;还没初始化时先初始化(建表并加载)
     */
    public DatabaseRuleStore store() {
        ensureInitialized();
        return store;
    }

    /**
     * 当前内存里规则对应的版本号
     */
    public long loadedVersion() {
        return loadedVersion;
    }

    /**
     * 重新加载后的回调,参数是规则有变化(新增、删除、修改)的类名;没有变化时不调用。
     * 轮询发现别的实例修改了规则、回滚、手动 {@link #reload()} 时触发,用来重新生成这些类的文档和字段规则。
     */
    public void setReloadListener(Consumer<Set<String>> reloadListener) {
        this.reloadListener = reloadListener;
    }

    // ------------------------------------------------------------------ 初始化 / 加载 / 轮询

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (lock) {
            if (initialized) {
                return;
            }
            if (closed) {
                throw new IllegalStateException("DatabaseParamGroupStrategy 已关闭");
            }
            try {
                DatabaseRuleStore s = storeSupplier.get();
                if (readOnly) {
                    s.checkSchema();
                } else {
                    s.ensureSchema(initSchema);
                }
                this.store = s;
                loadInto(s);
                LOG.info("csap-apidoc: 数据库模式已就绪 dialect={} service={} tablePrefix={} 规则 {} 个接口,版本 {}{}",
                        s.dialect(), s.service(), s.tablePrefix(), countMethods(rules), loadedVersion,
                        readOnly ? "(只读)" : "");
            } catch (SQLException e) {
                throw new IllegalStateException("csap-apidoc 数据库模式初始化失败: " + e.getMessage(), e);
            }
            initialized = true;
            startPoller();
        }
    }

    private void startPoller() {
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            return;
        }
        long ms = Math.max(50L, pollInterval.toMillis());
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "csap-apidoc-database-poller");
            t.setDaemon(true);
            return t;
        });
        poller.scheduleWithFixedDelay(this::pollOnce, ms, ms, TimeUnit.MILLISECONDS);
    }

    /**
     * 检查一次版本号,变了就重新加载。轮询线程调用,也可在测试里直接调用。
     *
     * @return 是否重新加载了
     */
    public boolean pollOnce() {
        if (!initialized || closed) {
            return false;
        }
        try {
            long v = store.version();
            boolean reloaded = false;
            if (v != loadedVersion) {
                reload();
                reloaded = true;
            }
            if (pollFailing) {
                LOG.info("csap-apidoc: 数据库模式轮询已恢复");
                pollFailing = false;
            }
            return reloaded;
        } catch (Exception e) {
            if (!pollFailing) {
                LOG.warn("csap-apidoc: 轮询规则版本号失败,继续使用内存中的规则: {}", e.toString());
                pollFailing = true;
            }
            return false;
        }
    }

    /**
     * 从数据库重新加载全部规则
     */
    public void reload() {
        ensureInitialized();
        Set<String> changed;
        synchronized (lock) {
            try {
                changed = loadInto(store);
            } catch (SQLException e) {
                throw new IllegalStateException("重新加载规则失败: " + e.getMessage(), e);
            }
        }
        Consumer<Set<String>> l = reloadListener;
        if (l != null && !changed.isEmpty()) {
            try {
                l.accept(changed);
            } catch (RuntimeException e) {
                LOG.warn("csap-apidoc: reload 回调失败: {}", e.toString());
            }
        }
    }

    private Set<String> loadInto(DatabaseRuleStore s) throws SQLException {
        DatabaseRuleStore.Snapshot snapshot = s.load();
        Map<String, Map<String, ParamGroupMethodProperty>> fresh = new ConcurrentHashMap<>(16);
        for (Map.Entry<String, String> e : snapshot.rules().entrySet()) {
            String key = e.getKey();
            int dot = key.lastIndexOf('.');
            if (dot <= 0 || dot == key.length() - 1) {
                LOG.warn("csap-apidoc: 规则键格式不对,应为 类全名.方法名,已忽略: {}", key);
                continue;
            }
            try {
                fresh.computeIfAbsent(key.substring(0, dot), k -> new ConcurrentHashMap<>(16))
                     .put(key.substring(dot + 1), RuleJson.read(e.getValue()));
            } catch (Exception ex) {
                LOG.warn("csap-apidoc: 规则 {} 的 JSON 无法解析,已忽略: {}", key, ex.getMessage());
            }
        }
        Set<String> changed = diff(this.rules, fresh);
        this.rules = fresh;
        this.loadedVersion = snapshot.version();
        return changed;
    }

    /**
     * 新旧规则里有差异(新增、删除、修改)的类名
     */
    private static Set<String> diff(Map<String, Map<String, ParamGroupMethodProperty>> before,
                                    Map<String, Map<String, ParamGroupMethodProperty>> after) {
        Set<String> changed = new TreeSet<>();
        Set<String> classes = new java.util.HashSet<>(before.keySet());
        classes.addAll(after.keySet());
        for (String cls : classes) {
            Map<String, ParamGroupMethodProperty> b = before.getOrDefault(cls, java.util.Collections.emptyMap());
            Map<String, ParamGroupMethodProperty> a = after.getOrDefault(cls, java.util.Collections.emptyMap());
            Set<String> methods = new java.util.HashSet<>(b.keySet());
            methods.addAll(a.keySet());
            for (String m : methods) {
                ParamGroupMethodProperty x = b.get(m);
                ParamGroupMethodProperty y = a.get(m);
                if (x == null || y == null || !RuleJson.write(x).equals(RuleJson.write(y))) {
                    changed.add(cls);
                    break;
                }
            }
        }
        return changed;
    }

    private static int countMethods(Map<String, Map<String, ParamGroupMethodProperty>> map) {
        return map.values().stream().mapToInt(Map::size).sum();
    }

    @Override
    public void close() {
        closed = true;
        ScheduledExecutorService p = poller;
        if (p != null) {
            p.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ 规则查找

    @Override
    public Map<String, Map<String, ParamGroupMethodProperty>> getHandle(StrategyModel strategyModel,
                                                                        CsapDocMethod method) {
        ensureInitialized();
        return rules;
    }

    /**
     * DevTools 保存前会直接修改本方法返回的规则,所以返回的是深拷贝:保存失败(只读、数据库出错)时内存里的规则不受影响。
     */
    @Override
    public Map<String, Map<String, ParamGroupMethodProperty>> handle(StrategyModel strategyModel,
                                                                     ApidocStrategyName strategyType, String path,
                                                                     String prefixName) {
        ensureInitialized();
        Map<String, Map<String, ParamGroupMethodProperty>> copy = new HashMap<>(16);
        rules.forEach((className, methods) -> {
            Map<String, ParamGroupMethodProperty> m = new HashMap<>(16);
            methods.forEach((name, property) -> m.put(name, RuleJson.copy(property)));
            copy.put(className, m);
        });
        return copy;
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate requestBasicParams(StrategyModel strategyModel,
                                                                         String keyName, CsapDocMethod docMethod,
                                                                         Method method, String paramName,
                                                                         Parameter parameter) {
        ParamGroupMethodProperty.ParamDataValidate dataValidate = requestGroup(strategyModel,
                keyName + DOT + paramName, docMethod, parameter.getType(), parameter.getAnnotations());
        if (Objects.nonNull(dataValidate)
                && Objects.nonNull(ApidocUtils.getParameterAnnotation(parameter, PathVariable.class))) {
            dataValidate.setParamType(ParamType.PATH);
        }
        return dataValidate;
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate paramRequestGroup(StrategyModel strategyModel, String keyName,
                                                                        CsapDocMethod docMethod, Method method,
                                                                        Field field) {
        return requestGroup(strategyModel, keyName, docMethod, field.getType(), field.getAnnotations());
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate paramResponseGroup(StrategyModel strategyModel,
                                                                         String keyName, CsapDocMethod docMethod,
                                                                         Method method, Field field) {
        return paramDataValidate(strategyModel, keyName, docMethod, false);
    }

    /**
     * 请求参数:返回缓存规则的副本,校验列表 = 库里的规则 + 注解规则。
     * 不能直接往缓存对象的列表里追加注解规则,否则每重新生成一次文档就会重复累加一份。
     */
    private ParamGroupMethodProperty.ParamDataValidate requestGroup(StrategyModel strategyModel, String keyName,
                                                                    CsapDocMethod docMethod, Class<?> type,
                                                                    Annotation[] annotations) {
        return ApidocOptional.ofNullable(paramDataValidate(strategyModel, keyName, docMethod, true))
                             .map(DatabaseParamGroupStrategy::copyOf)
                             .isNotNullCondition(i -> Objects.nonNull(validateFactory),
                                     i -> i.getValidate().addAll(validateFactory
                                             .getAllFieldConstraintValidator(type, annotations)))
                             .isNotNullCondition(i -> CollectionUtil.isNotEmpty(i.getValidate()),
                                     i -> i.getValidate().sort(Comparator.comparingInt(
                                             Validate.ConstraintValidatorField::getLevel)))
                             .get();
    }

    private static ParamGroupMethodProperty.ParamDataValidate copyOf(ParamGroupMethodProperty.ParamDataValidate source) {
        ParamGroupMethodProperty.ParamDataValidate copy = new ParamGroupMethodProperty.ParamDataValidate(
                source.getRequired(), source.getInclude(), source.getParamType(), source.getDescription(), null);
        copy.getValidate().addAll(source.getValidate());
        return copy;
    }

    // ------------------------------------------------------------------ 保存

    @Override
    public void writeAndMerge(StrategyModel strategyModel,
                              Map<String, Map<String, ParamGroupMethodProperty>> dataMap,
                              ApidocStrategyName strategyType, String className, String methodName,
                              Boolean request) {
        if (readOnly) {
            throw new RuleStorageNotWritableException(
                    "数据库模式处于只读状态(csap.apidoc.database.read-only=true),DevTools 不能保存规则。"
                            + "规则请由有写权限的环境修改,本环境会按 poll-interval 自动同步。"
                            + " | The database rule store is read-only (csap.apidoc.database.read-only=true); "
                            + "DevTools cannot save rules here. Change them in a writable environment; "
                            + "this instance picks the changes up by polling.");
        }
        ensureInitialized();
        Map<String, ParamGroupMethodProperty> methods = dataMap == null ? null : dataMap.get(className);
        ParamGroupMethodProperty property = methods == null ? null : methods.get(methodName);
        String key = className + "." + methodName;
        String json = RuleJson.isEmpty(property) ? null : RuleJson.write(property);
        synchronized (lock) {
            long version;
            try {
                version = store.save(key, json, UPDATED_BY);
            } catch (SQLException | RuntimeException e) {
                RuleStorageNotWritableException ex = new RuleStorageNotWritableException(
                        "保存规则到数据库失败(" + root(e) + ")。请检查数据库连接和账号的写权限(表 "
                                + store.tablePrefix() + "rule / history / meta)。"
                                + " | Failed to save the rule to the database (" + root(e) + "). "
                                + "Check the connection and the write permission on the rule tables.", e);
                LOG.warn("csap-apidoc: 保存规则 {} 失败: {}", key, ex.getMessage());
                throw ex;
            }
            if (version < 0) {
                return;
            }
            // 缓存同步:用保存后的内容(经 JSON 往返,与重新加载的结果一致)
            Map<String, Map<String, ParamGroupMethodProperty>> cache = rules;
            if (json == null) {
                Map<String, ParamGroupMethodProperty> m = cache.get(className);
                if (m != null) {
                    m.remove(methodName);
                    if (m.isEmpty()) {
                        cache.remove(className, m);
                    }
                }
            } else {
                try {
                    cache.computeIfAbsent(className, k -> new ConcurrentHashMap<>(16))
                         .put(methodName, RuleJson.read(json));
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            }
            // 只有本次保存紧接在已加载的版本之后,才能认为缓存已是最新;否则留给轮询去重新加载
            if (loadedVersion + 1 == version) {
                loadedVersion = version;
            }
        }
    }

    private static String root(Throwable e) {
        Throwable r = e;
        while (r.getCause() != null && r.getCause() != r) {
            r = r.getCause();
        }
        return r.getClass().getSimpleName() + ": " + r.getMessage();
    }

    // ------------------------------------------------------------------ 修改记录 / 回滚

    /**
     * 修改记录,最新的在前
     */
    @Override
    public List<RuleChange> history(String ruleKey, int limit) {
        try {
            return store().history(ruleKey, limit);
        } catch (SQLException e) {
            throw new IllegalStateException("读取修改记录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 回滚到某条修改记录之前的内容(回滚本身也是一次保存,会留下新的记录)
     *
     * @return 新版本号;已经是那个内容时返回 -1
     */
    @Override
    public long rollback(long changeId, String updatedBy) {
        if (readOnly) {
            throw new RuleStorageNotWritableException("数据库模式处于只读状态,不能回滚规则。"
                    + " | The database rule store is read-only; rules cannot be rolled back.");
        }
        synchronized (lock) {
            try {
                long v = store().rollback(changeId, updatedBy);
                if (v >= 0) {
                    reload();
                }
                return v;
            } catch (SQLException e) {
                throw new IllegalStateException("回滚失败: " + e.getMessage(), e);
            }
        }
    }
}
