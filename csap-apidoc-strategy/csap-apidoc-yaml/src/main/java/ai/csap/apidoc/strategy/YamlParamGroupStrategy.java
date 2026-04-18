package ai.csap.apidoc.strategy;

import static ai.csap.apidoc.handle.IStandardHandle.splitName;
import static ai.csap.apidoc.util.IValidate.DOT;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.strategy.handle.MethodFieldHandle;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;

import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Yaml参数分组策略.
 * <p>Created on 2021/11/16
 *
 * @author ycf
 * @since 1.0
 */
@Slf4j
public class YamlParamGroupStrategy implements ParamGroupStrategy {
    /**
     * 配置处理
     */
    private static final Map<String, Map<String, Map<String, ParamGroupMethodProperty>>>
            HANDLE_MAP = new ConcurrentHashMap<>(16);

    /**
     * 方法字段处理
     */
    private final MethodFieldHandle methodFieldHandle;
    /**
     * 文档配置
     */
    private final EnableApidocConfig enableApidocConfig;
    /**
     * 是否开发模式
     */
    private final CsapDocConfig csapDocConfig;
    /**
     * 字段验证工厂
     */
    private final IValidateFactory validateFactory;

    public YamlParamGroupStrategy(MethodFieldHandle methodFieldHandle,
                                  EnableApidocConfig enableApidocConfig,
                                  CsapDocConfig csapDocConfig,
                                  IValidateFactory validateFactory,
                                  ApplicationContext applicationContext) {
        this.methodFieldHandle = methodFieldHandle;
        this.enableApidocConfig = enableApidocConfig;
        this.validateFactory = validateFactory;
        this.csapDocConfig = csapDocConfig;
    }

    @Override
    public String getName() {
        return ApiStrategyType.YAML.getName();
    }

    @Override
    public String getSuffix() {
        return ApiStrategyType.YAML.getSuffix();
    }

    @Override
    public ApidocStrategyName strategyType() {
        return this;
    }

    /**
     * 获取指定处理器结果
     *
     * @param method 方法对象
     * @return 结果
     */
    @Override
    public Map<String, Map<String, ParamGroupMethodProperty>> getHandle(StrategyModel strategyModel,
                                                                        CsapDocMethod method) {
        if (HANDLE_MAP.containsKey(strategyModel.getId())) {
            return HANDLE_MAP.get(strategyModel.getId());
        }
        Map<String, Map<String, ParamGroupMethodProperty>> handle = methodFieldHandle.handle(this, enableApidocConfig.getPath(), enableApidocConfig
                .getPrefixStrategy().getName());
        HANDLE_MAP.put(strategyModel.getId(), handle);
        return HANDLE_MAP.get(strategyModel.getId());
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate requestBasicParams(StrategyModel strategyModel,
                                                                         String keyName, CsapDocMethod docMethod,
                                                                         Method method, String paramName,
                                                                         Parameter parameter) {
        ParamGroupMethodProperty.ParamDataValidate dataValidate = requestGroup(strategyModel,
                keyName + DOT + paramName, docMethod, method, parameter.getType(),
                parameter.getAnnotations());
        if (Objects.isNull(dataValidate)) {
            dataValidate = new ParamGroupMethodProperty.ParamDataValidate(false, true, null, null, null);
        }
        if (Objects.nonNull(parameter.getAnnotation(PathVariable.class))) {
            dataValidate.setParamType(ParamType.PATH);
        }
        return dataValidate;
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate paramRequestGroup(StrategyModel strategyModel, String keyName,
                                                                        CsapDocMethod docMethod, Method method,
                                                                        Field field) {
        return requestGroup(strategyModel, keyName, docMethod, method, field.getType(), field.getAnnotations());
    }

    /**
     * 通用的请求参数处理
     *
     * @param keyName     字段拼接名称
     * @param method      方法
     * @param type        字段类型
     * @param annotations 字段注解
     * @return 方法验证属性
     */
    private ParamGroupMethodProperty.ParamDataValidate requestGroup(StrategyModel strategyModel,
                                                                    String keyName, CsapDocMethod docMethod,
                                                                    Method method, Class<?> type,
                                                                    Annotation[] annotations) {
        return ApidocOptional.ofNullable(paramDataValidate(strategyModel, keyName, docMethod, true))
                             .isNotNullCondition(i -> Objects.nonNull(validateFactory),
                                     i -> i.getValidate().addAll(validateFactory
                                             .getAllFieldConstraintValidator(type, annotations)))
                             .isNotNullCondition(i -> CollectionUtil.isNotEmpty(i.getValidate()),
                                     i -> i.getValidate().sort(Comparator.comparingInt(
                                             Validate.ConstraintValidatorField::getLevel)))
                             .get();
    }

    @Override
    public ParamGroupMethodProperty.ParamDataValidate paramResponseGroup(StrategyModel strategyModel,
                                                                         String keyName, CsapDocMethod docMethod,
                                                                         Method method, Field field) {
        return paramDataValidate(strategyModel, keyName, docMethod, false);
    }

    @Override
    public Map<String, Map<String, ParamGroupMethodProperty>> handle(StrategyModel strategyModel,
                                                                     ApidocStrategyName strategyType, String path,
                                                                     String prefixName) {
        return methodFieldHandle.handle(strategyType, path, splitName(prefixName));
    }

    @Override
    public void writeAndMerge(StrategyModel strategyModel,
                              Map<String, Map<String, ParamGroupMethodProperty>> dataMap,
                              ApidocStrategyName strategyType,
                              String className, String methodName, Boolean request) {
        methodFieldHandle.writeAndMerge(dataMap, strategyType);
        //刷新
        HANDLE_MAP.computeIfAbsent(strategyModel.getId(), i -> new HashMap<>(16)).putAll(dataMap);
    }
}
