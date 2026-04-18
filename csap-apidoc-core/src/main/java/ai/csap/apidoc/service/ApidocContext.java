package ai.csap.apidoc.service;

import static ai.csap.apidoc.strategy.ApidocStrategy.DEFAULT;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import ai.csap.apidoc.CsapResourcesProvider;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.strategy.ApidocStrategy;
import ai.csap.apidoc.strategy.AsyncTaskUtil;
import ai.csap.apidoc.strategy.ParamGroupStrategy;

import lombok.extern.slf4j.Slf4j;

/**
 * Apidoc Context
 *
 * <p>Uses {@link SmartInitializingSingleton} to ensure all strategy beans
 * are loaded after all singletons are initialized, including lazy-loaded beans.
 *
 * @Author ycf
 * @Date 2023/3/7 21:13
 * @Version 1.0
 */
@Slf4j
public class ApidocContext implements ApplicationContextAware, SmartInitializingSingleton {

    /**
     * ApplicationContext for loading beans
     */
    private ApplicationContext applicationContext;
    /**
     * 扫描配置
     */
    private static ScannerPackageConfig config;
    private static CsapDocConfig csapDocConfig;
    /**
     * 文档策略
     */
    private static final Map<String, ApidocStrategy> STRATEGY_MAP = new HashMap<>(3);
    /**
     * 参数策略
     */
    private static final Map<String, ParamGroupStrategy> STRATEGY_PARAM_MAP = new HashMap<>(3);

    /**
     * 方法请求策略
     */
    private static final Map<String, IMethodRequest> METHOD_REQUEST_MAP = new HashMap<>(3);
    /**
     * 方法返回策略
     */
    private static final Map<String, IMethodResponse> METHOD_RESPONSE_MAP = new HashMap<>(3);
    /**
     * 资源提供者
     */
    private static CsapResourcesProvider csapResourcesProvider;
    /**
     * 文档结果
     */
    private static CsapDocResponse csapDocResponse = null;

    /**
     * 默认构造函数
     *
     * @param resourcesProvider    资源提供者
     * @param scannerPackageConfig 扫描配置
     */
    public ApidocContext(CsapResourcesProvider resourcesProvider, ScannerPackageConfig scannerPackageConfig, CsapDocConfig docConfig) {
        csapResourcesProvider = resourcesProvider;
        config = scannerPackageConfig;
        csapDocConfig = docConfig;
    }

    public static Boolean isCache() {
        return csapDocConfig.getDevtool().getCache();
    }

    public static Boolean isDevEnabled() {
        return csapDocConfig.getDevtool().getEnabled();
    }

    /**
     * 文档类型
     *
     * @param strategyType 策略类型
     * @return 结果
     */
    public static ApidocStrategy apidocStrategy(ApidocStrategyName strategyType) {
        return STRATEGY_MAP.get(strategyType.getName());
    }

    /**
     * 获取请求参数 执行方法
     *
     * @param name 策略名称
     * @return 请求参数执行方法
     */
    public static IMethodRequest getMethodRequest(String name) {
        return Optional.ofNullable(METHOD_REQUEST_MAP.get(name)).orElseThrow(() -> new RuntimeException(String.format("get %s methodRequest is null", name)));
    }

    /**
     * 获取返回参数 执行方法
     *
     * @param name 策略名称
     * @return 返回参数执行方法
     */
    public static IMethodResponse getMethodResponse(String name) {
        return Optional.ofNullable(METHOD_RESPONSE_MAP.get(name)).orElseThrow(() -> new RuntimeException(String.format("get %s methodResponse is null", name)));
    }

    /**
     * 参数处理类型
     *
     * @param strategyType 策略类型
     * @return 结果
     */
    public static ParamGroupStrategy paramStrategy(ApidocStrategyName strategyType) {
        return STRATEGY_PARAM_MAP.get(strategyType.getName());
    }

    /**
     * 执行文档入口(默认运行执行)
     */
    public static CsapDocResponse cmd(Boolean flush, Boolean isParent) {
        if (Objects.nonNull(csapDocResponse)) {
            return csapDocResponse;
        }
        csapDocResponse = new CsapDocResponse();
        List<StrategyModel> classeList = config.getApiClasseList();
        csapDocResponse.getGroups().addAll(Lists.newArrayList(DEFAULT));
        csapDocResponse.getVersions().addAll(Lists.newArrayList(DEFAULT));
        if (flush) {
            csapDocResponse.getApiList().clear();
        }
        log.info("start load apidoc....");
        // 使用 waitAllIgnoreException 确保即使部分文档加载失败，也能继续加载其他文档
        // 这样可以保证系统的可用性，同时会记录失败信息便于排查问题
        int failedCount = AsyncTaskUtil.waitAllIgnoreException(
                classeList.stream()
                        .map(i -> AsyncTaskUtil.submit(() -> {
                            i.setFlush(flush);
                            apidocStrategy(i.getDocType()).apidoc(null, isParent, i, csapDocResponse);
                        }))
                        .collect(Collectors.toList())
        );
        if (failedCount > 0) {
            log.warn("apidoc 加载完成，但有 {} 个文档加载失败，请查看上方日志", failedCount);
        } else {
            log.info("apidoc 加载完成，所有文档加载成功");
        }
        csapDocResponse.setApiInfo(config
                .getConfig()
                .getApiInfo());
        csapDocResponse.sortApi();
        csapDocResponse.setResources(Objects.nonNull(csapResourcesProvider) ? csapResourcesProvider.get() : null);
        return csapDocResponse;
    }

    /**
     * 执行
     *
     * @param className 类名称
     * @param methodKey 方法标识
     * @param flush     强制刷新
     */
    public static CsapDocResponse cmd(String className, String methodKey, Boolean flush) {
        if (!isDevEnabled()) {
            return csapDocResponse;
        }
        config.getApiClasseList().stream()
                .filter(i -> i.getClazz().stream().anyMatch(i2 -> i2.getName().equals(className)))
                .map(i -> StrategyModel.builder()
                        .key(methodKey)
                        .flush(flush)
                        .id(i.getId())
                        .fileName(i.getFileName())
                        .clazz(Collections.synchronizedSet(Sets.newHashSet(i.getClazz().stream().filter(i2 -> i2.getName().equals(className)).findFirst().orElseThrow())))
                        .paramType(i.getParamType())
                        .requestType(i.getRequestType())
                        .responseType(i.getResponseType())
                        .docType(i.getDocType())
                        .path(i.getPath())
                        .build())
                .forEach(i -> apidocStrategy(i.getDocType()).apidoc(className, false, i, csapDocResponse));
        csapDocResponse.setApiInfo(config
                .getConfig()
                .getApiInfo());
        csapDocResponse.setResources(Objects.nonNull(csapResourcesProvider) ? csapResourcesProvider.get() : null);
        return csapDocResponse;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Called after all singleton beans are fully initialized.
     * Loads all strategy beans at the optimal time, ensuring lazy-loaded beans are available.
     *
     * @see SmartInitializingSingleton
     */
    @Override
    public void afterSingletonsInstantiated() {
        // Load ApidocStrategy beans
        applicationContext.getBeansOfType(ApidocStrategy.class)
                .forEach((k, v) -> STRATEGY_MAP.put(v.getName(), v));

        // Load ParamGroupStrategy beans
        applicationContext.getBeansOfType(ParamGroupStrategy.class)
                .forEach((k, v) -> STRATEGY_PARAM_MAP.put(v.strategyType().getName(), v));

        // Load IMethodRequest beans
        applicationContext.getBeansOfType(IMethodRequest.class)
                .forEach((k, v) -> METHOD_REQUEST_MAP.put(v.name(), v));

        // Load IMethodResponse beans
        applicationContext.getBeansOfType(IMethodResponse.class)
                .forEach((k, v) -> METHOD_RESPONSE_MAP.put(v.name(), v));

        log.info("Loaded Apidoc strategy beans: {} ApidocStrategy, {} ParamGroupStrategy, {} MethodRequest, {} MethodResponse",
                 STRATEGY_MAP.size(), STRATEGY_PARAM_MAP.size(), METHOD_REQUEST_MAP.size(), METHOD_RESPONSE_MAP.size());
    }
}
