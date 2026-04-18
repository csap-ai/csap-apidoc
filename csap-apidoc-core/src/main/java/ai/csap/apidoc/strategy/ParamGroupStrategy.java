package ai.csap.apidoc.strategy;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;

import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;

/**
 * 参数分组策略
 *
 * @Author ycf
 * @Date 2021/11/16 10:50 下午
 * @Version 1.0
 */
public interface ParamGroupStrategy extends ApidocStrategyName {
    Logger LOGGER = LoggerFactory.getLogger(ParamGroupStrategy.class);

    /**
     * 请求参数 基础数据类型
     *
     * @param keyName   基本类型
     * @param method    方法
     * @param paramName 参数名称
     * @param parameter 当前字段
     * @return 返回方法参数属性
     */
    ParamGroupMethodProperty.ParamDataValidate requestBasicParams(StrategyModel strategyModel,
                                                                  String keyName, CsapDocMethod docMethod,
                                                                  Method method, String paramName,
                                                                  Parameter parameter);


    /**
     * 参数分组信息
     *
     * @param keyName   基本类型
     * @param docMethod 方法参数
     * @param method    方法
     * @param field     当前字段
     * @return 返回方法参数属性
     */
    ParamGroupMethodProperty.ParamDataValidate paramRequestGroup(StrategyModel strategyModel, String keyName, CsapDocMethod docMethod, Method method, Field field);

    /**
     * 参数分组信息
     *
     * @param keyName   基本类型
     * @param docMethod 方法参数
     * @param method    实际方法
     * @param field     当前字段
     * @return 返回方法参数属性
     */
    ParamGroupMethodProperty.ParamDataValidate paramResponseGroup(StrategyModel strategyModel, String keyName, CsapDocMethod docMethod, Method method, Field field);

    /**
     * 字段处理器
     *
     * @param strategyType 策略类型
     * @param path         路径
     * @param prefixName   前缀名称
     * @return 结果
     */
    Map<String, Map<String, ParamGroupMethodProperty>> handle(StrategyModel strategyModel, ApidocStrategyName strategyType, String path, String prefixName);

    /**
     * 写入且合并数据
     *
     * @param dataMap      数据
     * @param strategyType 策略
     * @param className    类名称
     * @param methodName   方法名称
     * @param request      是否请求参数 否则返回参数
     */
    void writeAndMerge(StrategyModel strategyModel,
                       Map<String, Map<String, ParamGroupMethodProperty>> dataMap,
                       ApidocStrategyName strategyType, String className, String methodName,
                       Boolean request);

    /**
     * 获取处理数据
     *
     * @param method 方法参数
     * @return 结果
     */
    Map<String, Map<String, ParamGroupMethodProperty>> getHandle(StrategyModel strategyModel, CsapDocMethod method);

    /**
     * 获取参数验证
     *
     * @param keyName 字段拼接的键
     * @param method  方法
     * @param request 是否请求
     * @return 参数数据验证
     */
    default ParamGroupMethodProperty.ParamDataValidate paramDataValidate(StrategyModel strategyModel, String keyName, CsapDocMethod method, boolean request) {
        ParamGroupMethodProperty paramGroupMethodProperty = yamlMethodProperty(strategyModel, method);
        if (Objects.isNull(paramGroupMethodProperty)) {
            return null;
        }
        if (request && MapUtil.isNotEmpty(paramGroupMethodProperty.getRequest()) && paramGroupMethodProperty.getRequest().containsKey(keyName)) {
            return paramGroupMethodProperty.getRequest().get(keyName);
        } else if (MapUtil.isNotEmpty(paramGroupMethodProperty.getResponse()) && paramGroupMethodProperty.getResponse().containsKey(keyName)) {
            return paramGroupMethodProperty.getResponse().get(keyName);
        }
        return null;
    }

    /**
     * yaml属性信息
     *
     * @param method 方法
     * @return 方法属性
     */
    default ParamGroupMethodProperty yamlMethodProperty(StrategyModel strategyModel, CsapDocMethod method) {
        Map<String, Map<String, ParamGroupMethodProperty>> handle = getHandle(strategyModel, method);
        if (CollectionUtil.isEmpty(handle)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("yamlMethodProperty get data result is empty:{},{}", method.getClassName(), method.getName());
            }
        }
        if (!handle.containsKey(method.getClassName())) {
            return null;
        }
        if (!handle.get(method.getClassName()).containsKey(method.getName())) {
            return null;
        }
        return handle.get(method.getClassName()).get(method.getName());
    }

}
