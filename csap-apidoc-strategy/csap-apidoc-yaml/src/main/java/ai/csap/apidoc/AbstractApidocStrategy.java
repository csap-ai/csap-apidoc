package ai.csap.apidoc;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.properties.CsapApiInfo;
import ai.csap.apidoc.strategy.ApidocStrategy;
import ai.csap.apidoc.strategy.handle.ControllerHandle;
import ai.csap.apidoc.strategy.handle.EnumHandle;
import ai.csap.apidoc.strategy.handle.InfoHandle;
import ai.csap.apidoc.strategy.handle.MethodFieldHandle;
import ai.csap.apidoc.strategy.handle.MethodHandle;
import ai.csap.apidoc.strategy.handle.ParamHandle;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * API文档策略抽象基类
 * 定义了文档生成的通用处理流程和模板方法
 *
 * <p>核心设计：</p>
 * <ul>
 *   <li>采用模板方法模式，定义文档生成的标准流程</li>
 *   <li>使用责任链模式，按顺序处理各个文档组件</li>
 *   <li>支持多种数据源策略（YAML、JSON、SQLite等）</li>
 *   <li>提供统一的读写接口，屏蔽底层实现差异</li>
 * </ul>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>加载配置属性 - properties()</li>
 *   <li>处理Controller信息 - controllerHandle.handle()</li>
 *   <li>处理枚举信息 - enumHandle.handle()</li>
 *   <li>处理基本信息 - infoHandle.handle()</li>
 *   <li>处理方法信息 - methodHandle.handle()</li>
 *   <li>处理参数信息 - paramHandle.handle()</li>
 *   <li>过滤方法数据 - methodFilter()</li>
 *   <li>组装响应结果</li>
 * </ol>
 *
 * <p>子类需要实现：</p>
 * <ul>
 *   <li>strategyType() - 返回具体的策略类型</li>
 *   <li>可选地重写properties() - 提供特定的配置加载逻辑</li>
 * </ul>
 *
 * @Author ycf
 * @Date 2021/11/7 7:18 下午
 * @Version 1.0
 */
@Setter
@Slf4j
@Accessors(chain = true)
@RequiredArgsConstructor
public abstract class AbstractApidocStrategy implements ApidocStrategy {
    /**
     * Controller信息处理器
     * 负责读取和写入Controller的文档配置
     */
    public final ControllerHandle controllerHandle;

    /**
     * 枚举信息处理器
     * 负责处理响应码枚举的文档生成
     */
    public final EnumHandle enumHandle;

    /**
     * 基本信息处理器
     * 负责处理API文档的基本信息，如标题、描述、版本等
     */
    public final InfoHandle infoHandle;

    /**
     * 方法信息处理器
     * 负责处理接口方法的文档配置
     */
    public final MethodHandle methodHandle;

    /**
     * 方法字段信息处理器
     * 负责处理方法参数和返回值的字段级配置
     */
    public final MethodFieldHandle methodFieldHandle;

    /**
     * 参数信息处理器
     * 负责处理通用参数模型的文档配置
     */
    public final ParamHandle paramHandle;

    /**
     * API文档配置
     * 包含文档生成的全局配置信息
     */
    public final EnableApidocConfig enableApidocConfig;

    /**
     * 资源提供者（可选）
     * 用于提供额外的API资源信息
     */
    @Autowired(required = false)
    public CsapResourcesProvider resourcesProvider;

    @Override
    public abstract ApidocStrategyName strategyType();

    @Override
    public CsapDocResponse apidoc(String className,
                                  Boolean isParent,
                                  StrategyModel strategyModel,
                                  CsapDocResponse csapDocResponse) {
        return properties()
                .then(i -> controllerHandle.handle(i, strategyType(), enableApidocConfig.getPath()))
                .then(i -> enumHandle.handle(i, strategyType(), enableApidocConfig.getPath()))
                .then(i -> infoHandle.handle(i, strategyType(), enableApidocConfig.getPath()))
                .then(i -> methodHandle.handle(i, strategyType(), enableApidocConfig.getPath()))
                .then(i -> paramHandle.handle(i, strategyType(), enableApidocConfig.getPath()))
                .then(this::methodFilter)
                .map(i -> csapDocResponse
                        .setApiInfo(i.getApiInfo())
                        .setEnumList(i.getEnumMap())
                        .setApiList(Lists.newArrayList(i.getControllerMap().values()))
                        .setResources(resourcesProvider != null ? resourcesProvider.get() : null))
                .get();
    }


    /**
     * 获取所有Controller的文档映射
     * 从配置文件中读取Controller级别的文档信息
     *
     * @return Controller映射表，key为Controller全限定名，value为Controller文档模型
     */
    public Map<String, CsapDocModelController> controllerMap() {
        return controllerHandle.handle(strategyType(), enableApidocConfig.getPath());
    }

    /**
     * 获取所有枚举的文档映射
     * 从配置文件中读取响应码枚举的文档信息
     *
     * @return 枚举映射表，key为枚举类名，value为枚举值列表
     */
    public Map<String, List<Map<String, Object>>> enumMap() {
        return enumHandle.handle(strategyType(), enableApidocConfig.getPath());
    }

    /**
     * 获取所有方法的文档映射
     * 从配置文件中读取接口方法的文档信息
     *
     * @return 方法映射表，第一层key为Controller名，第二层key为方法名，value为方法文档模型
     */
    public Map<String, Map<String, CsapDocMethod>> methodMap() {
        return methodHandle.handle(strategyType(), enableApidocConfig.getPath());
    }

    /**
     * 获取方法字段级别的配置映射
     * 从配置文件中读取方法参数和返回值的字段级配置
     *
     * @return 方法字段映射表，第一层key为Controller名，第二层key为方法名，value为字段配置
     */
    public Map<String, Map<String, ParamGroupMethodProperty>> methodFieldMap() {
        return methodFieldHandle.handle(strategyType(), enableApidocConfig.getPath());
    }

    /**
     * 获取通用参数模型的映射
     * 从配置文件中读取可复用的参数模型定义
     *
     * @return 参数映射表，key为参数模型名，value为参数文档模型
     */
    public Map<String, CsapDocModel> paramMap() {
        return paramHandle.handle(strategyType(), enableApidocConfig.getPath());
    }

    /**
     * 获取API文档的基本信息
     * 从配置文件中读取文档的标题、描述、版本等基本信息
     *
     * @return API基本信息对象
     */
    public CsapApiInfo info() {
        return infoHandle.handle(strategyType(), enableApidocConfig.getPath());
    }

    /**
     * 写入文档到配置文件
     * 按照责任链模式依次写入各个组件的配置
     *
     * <p>写入顺序：</p>
     * <ol>
     *   <li>Controller配置</li>
     *   <li>枚举配置</li>
     *   <li>基本信息配置</li>
     *   <li>方法配置</li>
     *   <li>方法字段配置</li>
     *   <li>参数配置</li>
     * </ol>
     *
     * <p>注意：写入操作会自动合并现有配置，不会覆盖未涉及的内容</p>
     *
     * @param standardProperties 待写入的文档数据
     * @return true表示写入成功，false表示写入失败
     */
    public Boolean write(StandardProperties standardProperties) {
        return standardProperties
                .optional()
                .then(i -> controllerHandle.writeAndMerge(standardProperties.getControllerMap(), strategyType()))
                .then(i -> enumHandle.writeAndMerge(standardProperties.getEnumMap(), strategyType()))
                .then(i -> infoHandle.write(standardProperties.getApiInfo(), strategyType()))
                .then(i -> methodHandle.writeAndMerge(standardProperties.getMethodMap(), strategyType()))
                .then(i -> methodFieldHandle.writeAndMerge(standardProperties.getMethodFieldMap(), strategyType()))
                .then(i -> paramHandle.writeAndMerge(standardProperties.getParamMap(), strategyType()))
                .value(i -> Boolean.TRUE);
    }

    /**
     * 过滤和关联方法数据
     * 将Controller中定义的方法名与实际的方法文档数据进行匹配
     * 只保留在配置中存在的方法
     *
     * <p>过滤逻辑：</p>
     * <ul>
     *   <li>检查方法配置是否存在</li>
     *   <li>根据方法名从方法映射表中获取方法文档</li>
     *   <li>将方法文档设置到Controller的方法列表中</li>
     * </ul>
     *
     * @param i 包含Controller和方法映射的标准属性对象
     */
    public void methodFilter(StandardProperties i) {
        i.getControllerMap().forEach((k, v) -> v.setMethodList(v.getMethods().stream()
                .filter(i2 -> i.getMethodMap().containsKey(k))
                .filter(i2 -> i.getMethodMap().get(k).containsKey(i2))
                .map(i2 -> i.getMethodMap().get(k).get(i2))
                .collect(Collectors.toList())));
    }

    /**
     * 获取配置属性对象
     * 子类可以重写此方法，提供特定的配置加载逻辑
     *
     * <p>默认实现：返回一个空的标准属性对象</p>
     *
     * @return ApidocOptional包装的标准属性对象
     */
    public ApidocOptional<StandardProperties> properties() {
        return ApidocOptional.of(StandardProperties.builder().build());
    }
}
