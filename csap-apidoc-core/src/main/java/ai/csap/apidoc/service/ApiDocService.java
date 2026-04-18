package ai.csap.apidoc.service;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.CsapHeadersProvider;
import ai.csap.apidoc.CsapResourcesProvider;
import ai.csap.apidoc.DevtoolsClass;
import ai.csap.apidoc.GlobalCsapRequestParamProvider;
import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.annotation.ApiOperation;
import ai.csap.apidoc.annotation.ApiResponseCode;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocEnum;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocMethodHeaders;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.scanner.DocHintsCollector;
import ai.csap.apidoc.strategy.ApidocStrategy;
import ai.csap.apidoc.strategy.AsyncTaskUtil;
import ai.csap.apidoc.util.ApidocUtils;
import ai.csap.apidoc.util.IValidate;
import ai.csap.apidoc.util.TypeVariableModel;
import ai.csap.apidoc.util.ValidateException;
import ai.csap.validation.factory.IValidateFactory;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ai.csap.apidoc.util.IValidate.DOT;

/**
 * API文档核心处理服务
 * 基于注解方式扫描和解析Controller，生成API文档数据
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>扫描指定包路径下的所有Controller类</li>
 *   <li>解析@Api、@ApiOperation等注解信息</li>
 *   <li>提取方法的请求参数和返回值结构</li>
 *   <li>生成完整的API文档数据模型</li>
 *   <li>支持并发处理，提升大型项目的文档生成速度</li>
 * </ul>
 *
 * <p>处理流程：</p>
 * <ol>
 *   <li>扫描Controller类，筛选带有@RestController或@Controller注解的类</li>
 *   <li>过滤带有@Api注解的类，提取类级别的文档信息</li>
 *   <li>遍历类的方法，解析@ApiOperation注解，生成方法文档</li>
 *   <li>解析方法参数和返回值，构建请求响应模型</li>
 *   <li>收集枚举类型作为响应码文档</li>
 * </ol>
 *
 * @author yangchengfu
 * @date 2019/12/28 10:20 上午
 **/
@Slf4j
public final class ApiDocService extends AbstractApiDocService implements FilterData, ApidocStrategy {

    /**
     * 构造函数，注入文档生成所需的各种依赖
     *
     * @param devtoolsClass                  开发工具类，判断是否启用开发模式
     * @param packageConfig                  包扫描配置，指定要扫描的包路径
     * @param resourcesProvider              资源提供者，提供额外的API资源信息
     * @param csapHeadersProvider            请求头提供者，提供全局请求头配置
     * @param validateFactory                验证工厂，用于参数验证规则的生成
     * @param globalCsapRequestParamProvider 全局请求参数提供者，如分页参数
     * @param beanFactory                    Spring Bean工厂，用于获取Controller实例
     * @param csapDocConfig                  文档配置，包含文档生成的各种配置项
     */
    public ApiDocService(DevtoolsClass devtoolsClass, ScannerPackageConfig packageConfig,
                         CsapResourcesProvider resourcesProvider, CsapHeadersProvider csapHeadersProvider,
                         IValidateFactory validateFactory,
                         GlobalCsapRequestParamProvider globalCsapRequestParamProvider,
                         BeanFactory beanFactory, CsapDocConfig csapDocConfig) {
        super(devtoolsClass, packageConfig, resourcesProvider, csapHeadersProvider, validateFactory,
                globalCsapRequestParamProvider, beanFactory, csapDocConfig);
    }

    @Override
    public String getName() {
        return ApiStrategyType.ANNOTATION.getName();
    }

    @Override
    public String getSuffix() {
        return ApiStrategyType.ANNOTATION.getSuffix();
    }

    @Override
    public ApidocStrategyName strategyType() {
        return this;
    }

    /**
     * 处理apdoc包路径
     *
     * @param className     tableName
     * @param strategyModel 策略信息
     * @return apidoc response
     */
    @Override
    public CsapDocResponse apidoc(String className, Boolean isParent, StrategyModel strategyModel,
                                  CsapDocResponse csapDocResponse) {
        return scannerApiPackage(className, isParent, strategyModel, csapDocResponse);
    }

    /**
     * 扫描API包路径，生成文档数据
     * 支持并发扫描多个Controller，提升性能
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>检查是否启用开发工具模式</li>
     *   <li>并发处理每个Controller类，生成文档模型</li>
     *   <li>收集所有Controller的版本和分组信息</li>
     *   <li>等待所有异步任务完成（最多60秒）</li>
     *   <li>扫描枚举类型作为响应码文档</li>
     * </ol>
     *
     * @param className       指定要扫描的类名，为空则扫描所有
     * @param isParent        是否为父类接口（用于继承场景）
     * @param strategyModel   策略模型，包含扫描配置和类列表
     * @param csapDocResponse 文档响应对象，用于累积结果
     * @return 完整的文档响应对象，包含所有Controller和枚举信息
     */
    @SneakyThrows
    private CsapDocResponse scannerApiPackage(
            String className,
            Boolean isParent,
            StrategyModel strategyModel,
            CsapDocResponse csapDocResponse
    ) {
        Boolean devtools = getCsapDocConfig().getDevtool().getEnabled();
        List<CsapDocModelController> controllers = new ArrayList<>();

        // 检查是否有需要处理的类
        if (CollectionUtil.isNotEmpty(strategyModel.getClazz())) {
            // 为每个Controller类创建异步处理任务
            var futures = strategyModel.getClazz().stream()
                                       .map(cl -> (Supplier<CsapDocModelController>) () -> {
                                           CsapDocModelController controller = controller(StrategyModel
                                                   .builder()
                                                   .clz(cl)
                                                   .isParent(isParent)
                                                   .id(strategyModel.getId())
                                                   .fileName(strategyModel.getFileName())
                                                   .flush(strategyModel.getFlush())
                                                   .key(strategyModel.getKey())
                                                   .paramType(strategyModel.getParamType())
                                                   .devtools(devtools)
                                                   .requestType(strategyModel.getRequestType())
                                                   .responseType(strategyModel.getResponseType())
                                                   .docType(strategyModel.getDocType())
                                                   .path(strategyModel.getPath())
                                                   .build(), className);

                                           // 如果Controller为空则跳过
                                           if (Objects.isNull(controller)) {
                                               return null;
                                           }

                                           // 收集版本和分组信息到响应对象
                                           csapDocResponse.getVersions()
                                                          .addAll(controller.getVersion());
                                           csapDocResponse.getGroups()
                                                          .addAll(controller.getGroup());
                                           return controller;
                                       }).collect(Collectors.toList());

            // 并发执行所有任务，最多等待60秒
            CompletableFuture<List<CsapDocModelController>> listCompletableFuture = AsyncTaskUtil.submitAll(futures);
            controllers.addAll(listCompletableFuture.get(60, TimeUnit.SECONDS));
        }

        // 扫描枚举类型并添加到响应中，然后刷新API列表
        return scannerResultEnumPackage(csapDocResponse
                .setGlobalApiList(globalApiList)
                .flushApiList(controllers, strategyModel));
    }

    @Override
    public CsapDocModelController controller(StrategyModel strategyModel, String className) {
        Class<?> cl = strategyModel.getClz();
        if (StrUtil.isNotEmpty(className) && !filterTable(cl, className)) {
            return null;
        }
        if (AnnotationUtils.findAnnotation(cl, Controller.class) == null) {
            return null;
        }
        Api api = AnnotationUtils.findAnnotation(cl, Api.class);
        boolean ab = api == null;
        if (ab) {
            return null;
        }
        if (api.hidden()) {
            return null;
        }
        // 使用 AnnotatedElementUtils 支持从父类和接口获取 @RequestMapping
        // 优先级：实现类 > 接口 > 父类
        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(cl, RequestMapping.class);
        CsapDocModelController csapDocController = CsapDocModelController.builder()
                                                                         .description(api.description())
                                                                         .methodList(Collections.synchronizedList(new ArrayList<>()))
                                                                         .hidden(api.hidden())
                                                                         .name(cl.getName())
                                                                         .simpleName(cl.getSimpleName())
                                                                         .path(requestMapping == null || ArrayUtil.isEmpty(requestMapping.value()) ? null : requestMapping.value())
                                                                         .position(api.position())
                                                                         .protocols(api.protocols())
                                                                         .hiddenMethod(Collections.synchronizedList(Arrays.asList(api.hiddenMethod())))
                                                                         .showMethod(Collections.synchronizedList(Arrays.asList(api.showMethod())))
                                                                         .group(set(api.group()))
                                                                         .version(set(api.version()))
                                                                         .status(api.status())
                                                                         .tags(api.tags())
                                                                         .search(ConcurrentHashMap.newKeySet())
                                                                         .value(api.value()).build();
        csapDocController.getSearch().add(csapDocController.getValue());
        csapDocController.getSearch().add(csapDocController.getDescription());
        if (ArrayUtil.isNotEmpty(csapDocController.getTags())) {
            csapDocController.getSearch().add(ArrayUtil.toString(Arrays.asList(csapDocController.getTags())));
        }
        getMethods(csapDocController, cl.getMethods(), new TypeVariableModel(cl), strategyModel);
        return csapDocController;
    }

    /**
     * 扫描并生成响应码枚举文档
     * 提取标注了@ApiResponseCode的枚举类，生成响应码说明文档
     *
     * <p>处理条件：</p>
     * <ul>
     *   <li>类必须标注@ApiResponseCode注解</li>
     *   <li>类必须是枚举类型</li>
     *   <li>枚举必须实现IValidate接口（除外的其他接口也可以）</li>
     *   <li>枚举常量不能为空</li>
     * </ul>
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * @ApiResponseCode("业务错误码")
     * public enum ErrorCode implements IValidate<String, String> {
     *     SUCCESS("0", "成功"),
     *     PARAM_ERROR("1001", "参数错误");
     *     // ...
     * }
     * }</pre>
     *
     * @param csapDocResponse 文档响应对象，将枚举信息添加到此对象中
     * @return 更新后的文档响应对象
     */
    public CsapDocResponse scannerResultEnumPackage(CsapDocResponse csapDocResponse) {
        List<CsapDocEnum> list = null;

        // 检查是否配置了枚举类扫描
        if (CollectionUtil.isNotEmpty(getPackageConfig().getEnumClasseList())) {
            list = Lists.newArrayList();

            // 遍历所有配置的枚举类
            for (Class<?> cl : getPackageConfig().getEnumClasseList()) {
                // 过滤：必须有@ApiResponseCode注解
                if (!cl.isAnnotationPresent(ApiResponseCode.class)) {
                    continue;
                }
                // 过滤：必须是枚举类型
                if (!cl.isEnum()) {
                    continue;
                }

                // 创建枚举文档对象
                List<CsapDocEnum.CsapDocEnumCode> enumCodes = Lists.newArrayList();
                list.add(CsapDocEnum.builder()
                                    .name(cl.getName())
                                    .value(cl.getAnnotation(ApiResponseCode.class).value())
                                    .enumList(enumCodes)
                                    .build());

                // 检查枚举是否实现了除IValidate以外的接口
                boolean isIntface = false;
                for (Class<?> cll : cl.getInterfaces()) {
                    if (cll.getName().equals(IValidate.class.getName())) {
                        continue;
                    }
                    isIntface = true;
                }

                // 如果没有实现其他接口，跳过（要求枚举实现特定接口）
                if (!isIntface) {
                    continue;
                }

                // 获取所有枚举常量
                IValidate<?, ?>[] bcs = (IValidate<?, ?>[]) cl.getEnumConstants();
                if (ArrayUtil.isEmpty(bcs)) {
                    continue;
                }

                // 提取每个枚举常量的编码、名称和消息
                for (IValidate<?, ?> code : bcs) {
                    enumCodes.add(new CsapDocEnum.CsapDocEnumCode(
                            code.getName(),
                            code.getCode() instanceof String ? (String) code.getCode() : code.getCode().toString(),
                            code.getMessage()
                    ));
                }
            }
        }

        return csapDocResponse.addEnumList(list);
    }


    /**
     * 创建 HandlerMethod 用于文档生成
     * 多层降级策略确保100%成功创建实例
     *
     * @param clazz  Controller类
     * @param method 方法
     * @return HandlerMethod实例
     * @throws ValidateException 所有方式都失败时抛出异常
     */
    private HandlerMethod createHandlerMethodForDoc(Class<?> clazz, Method method) {
        Object instance = null;

        // 策略1: 从Spring容器获取Bean（Controller通常是Spring管理的Bean）
        if (beanFactory != null) {
            try {
                instance = beanFactory.getBean(clazz);
                return new HandlerMethod(instance, method);
            } catch (Exception e) {
                // Bean不存在，继续尝试其他方式
            }
        }

        // 策略2: 使用无参构造创建临时实例
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
            return new HandlerMethod(instance, method);
        } catch (Exception e) {
            // 没有无参构造，继续尝试其他方式
        }

        // 策略3: 使用Spring的BeanUtils.instantiateClass尝试其他构造函数
        try {
            instance = org.springframework.beans.BeanUtils.instantiateClass(clazz);
            return new HandlerMethod(instance, method);
        } catch (Exception e) {
            // BeanUtils失败，继续尝试其他方式
        }

        // 策略4: 使用Spring内置的Objenesis绕过构造函数（最终方案）
        try {
            instance = createInstanceWithObjenesis(clazz);
            return new HandlerMethod(instance, method);
        } catch (Exception e) {
            throw new ValidateException("无法为类 " + clazz.getName() + " 创建实例用于生成API文档。已尝试所有可用方式。", e);
        }
    }

    /**
     * 使用Spring自带的Objenesis创建实例（不调用构造函数）
     * Spring Boot已内置objenesis依赖，无需额外配置
     *
     * @param clazz 要实例化的类
     * @return 类的实例
     */
    private Object createInstanceWithObjenesis(Class<?> clazz) {
        try {
            // Spring Boot自带的objenesis
            org.springframework.objenesis.ObjenesisStd objenesis = new org.springframework.objenesis.ObjenesisStd();
            return objenesis.newInstance(clazz);
        } catch (Exception e) {
            throw new ValidateException("使用Objenesis创建实例失败: " + clazz.getName(), e);
        }
    }

    /**
     * 处理所有方法
     *
     * @param docController     controller
     * @param methods           methos
     * @param typeVariableModel 父类的类型参数
     */
    @SneakyThrows
    public void getMethods(CsapDocModelController docController,
                           Method[] methods,
                           TypeVariableModel typeVariableModel,
                           StrategyModel strategyModel) {
        List<CsapDocMethod> collect = Arrays.stream(methods)
                                            .filter(i -> {
                                                // if (StrUtil.isNotEmpty(strategyModel.getKey())) {
                                                //     return strategyModel.getKey().equals(strategyModel.getClz().getName() + DOT + i.getName());
                                                // }
                                                return true;
                                            })
                                            .map(i -> (Supplier<CsapDocMethod>) () -> method(docController, i, typeVariableModel, strategyModel))
                                            .map(Supplier::get)
                                            .filter(Objects::nonNull)
                                            .sorted(Comparator.comparing(CsapDocMethod::getName))
                                            .collect(Collectors.toList());
        docController.setMethodList(collect);
    }

    @Override
    public CsapDocMethod method(CsapDocModelController docController, Method method,
                                TypeVariableModel typeVariableModel, StrategyModel strategyModel) {
        Boolean hidden = Boolean.FALSE;
        if (CollectionUtil.isNotEmpty(docController.getHiddenMethod()) &&
                docController.getHiddenMethod().contains(method.getName())) {
            if (!getDevtoolsClass().devtools() || !docController.getDevTools()) {
                return null;
            }
            hidden = Boolean.TRUE;
        }
        if (method.isBridge()) {
            return null;
        }
        if (CollectionUtil.isNotEmpty(docController.getShowMethod()) && !docController.getShowMethod()
                                                                                      .contains(method.getName())) {
            return null;
        }

        // 创建 HandlerMethod（用于获取参数注解信息，支持从接口/父类继承）
        HandlerMethod handlerMethod;
        try {
            handlerMethod = createHandlerMethodForDoc(typeVariableModel.getAClass(), method);
        } catch (Exception e) {
            log.warn("无法为 {}.{} 创建HandlerMethod，跳过该方法的文档生成: {}",
                    typeVariableModel.getAClass().getSimpleName(), method.getName(), e.getMessage());
            return null;
        }

        // 使用 AnnotatedElementUtils 支持方法注解继承（实现类 > 接口 > 父类）
        ApiOperation apiOperation = AnnotatedElementUtils.findMergedAnnotation(method, ApiOperation.class);
        if (Objects.isNull(apiOperation)) {
            return null;
        }
        boolean anyBodyMatch = Arrays.stream(method.getParameters())
                                     .anyMatch(i -> ApidocUtils.hasParameterAnnotation(i, RequestBody.class));
        if (apiOperation.hidden()) {
            if (!getDevtoolsClass().devtools() || !docController.getDevTools()) {
                return null;
            } else {
                hidden = Boolean.TRUE;
            }
        }
        CsapDocMethod docMethod = forMapping(method, CsapDocMethod.builder()
                                                                  .hidden(hidden)
                                                                  .description(apiOperation.description())
                                                                  .key(docController.getName() + DOT + method.getName())
                                                                  .className(docController.getName())
                                                                  .simpleName(docController.getSimpleName())
                                                                  .name(method.getName())
                                                                  .request(Lists.newArrayList())
                                                                  .paramNames(Lists.newArrayList(Objects.requireNonNull(PARAMETER_NAME_DISCOVERER.getParameterNames(method))))
                                                                  .response(Lists.newArrayList())
                                                                  .methods(Lists.newArrayList())
                                                                  .apiPath(docController.getPath())
                                                                  .group(GROUP.containsAll(Arrays.asList(apiOperation.group())) ? GROUP : set(apiOperation.group(), docController.getGroup()))
                                                                  .version(VERSION.containsAll(Arrays.asList(apiOperation.version())) ? VERSION : set(apiOperation.version(), docController.getVersion()))
                                                                  .tags(apiOperation.tags())
                                                                  .search(Sets.newHashSet())
                                                                  .value(apiOperation.value())
                                                                  .status(apiOperation.status())
                                                                  .paramTypes(Sets.newHashSet())
                                                                  .paramType(apiOperation.paramType()
                                                                                         .equals(ParamType.DEFAULT) && anyBodyMatch ? ParamType.BODY : apiOperation.paramType())
                                                                  .methodHeaders(Lists.newArrayList()).build());
        if (CollectionUtil.isEmpty(docMethod.getMethods())) {
            return null;
        }
        if (containsPathMethod(docController, docMethod)) {
            return null;
        }
        getValidateFactory().clear(docController.getName() + DOT + method.getName());
        headers(apiOperation, docMethod);
        // M7: 收集 @DocGlobalHeader / @DocAuth 提示（仅供 try-it-out UI 预填）
        Class<?> controllerClass = typeVariableModel.getAClass();
        docMethod.setGlobalHeaderHints(DocHintsCollector.collectGlobalHeaderHints(controllerClass, method));
        docMethod.setAuthHint(DocHintsCollector.resolveAuthHint(controllerClass, method));
        docController.getGroup().addAll(docMethod.getGroup());
        docController.getVersion().addAll(docMethod.getVersion());
        docMethod.getSearch().add(docMethod.getValue());
        docMethod.getSearch().add(docMethod.getDescription());
        if (ArrayUtil.isNotEmpty(docMethod.getTags())) {
            docMethod.getSearch().add(ArrayUtil.toString(Arrays.asList(docMethod.getTags())));
        }
        docController.getSearch().add(docMethod.getSearch().toString());
        if (!strategyModel.getIsParent()) {
            ApidocContext.getMethodRequest(strategyModel.getRequestType())
                         .handle(docMethod, method.getGenericParameterTypes(), method, typeVariableModel, handlerMethod, strategyModel);
        }
        if (CollectionUtil.isNotEmpty(docMethod.getRequest())) {
            for (int i = 0; i < docMethod.getRequest().size(); i++) {
                docMethod.getRequest().get(i).setMethodParamName(docMethod.getParamNames().get(i));
            }
        }
        if (getPackageConfig().getConfig().getResult().containsPageMethod(method.getName())) {
            List<CsapDocModel> globalRequestParam = getGlobalRequestParam(method.getName());
            docMethod.addRequest(globalRequestParam);
            Set<String> paramTypes = globalRequestParam.stream()
                                                       .map(CsapDocModel::getParameters)
                                                       .flatMap(Collection::stream)
                                                       .map(CsapDocParameter::getParamType)
                                                       .map(ParamType::name)
                                                       .collect(Collectors.toSet());
            docMethod.getParamTypes().addAll(paramTypes);
        }
        if (!strategyModel.getIsParent()) {
            ApidocContext.getMethodResponse(strategyModel.getResponseType())
                         .handle(method, docMethod, typeVariableModel, handlerMethod, strategyModel);
        }
        if (CollectionUtil.isEmpty(docMethod.getParamTypes())) {
            docMethod.getParamTypes().add(docMethod.getParamType().name());
        }
        return docMethod;
    }

    @Override
    public Boolean write(StandardProperties standardProperties) {
        throw new ValidateException("not impl");
    }

    /**
     * 处理mapping映射
     * 支持从父类和接口继承 @RequestMapping 注解
     *
     * @param method    方法
     * @param docMethod 文档方法
     * @return CsapDocMethod
     */
    public static CsapDocMethod forMapping(Method method, CsapDocMethod docMethod) {
        // 使用 AnnotatedElementUtils 支持注解继承（实现类 > 接口 > 父类）
        RequestMapping methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (ArrayUtil.isEmpty(methodAnnotation.method())) {
            docMethod.getMethods()
                     .addAll(Lists.newArrayList(RequestMethod.POST, RequestMethod.DELETE, RequestMethod.POST, RequestMethod.GET));
        } else {
            docMethod.getMethods().addAll(Lists.newArrayList(methodAnnotation.method()));
        }

        docMethod.setPaths(methodAnnotation.path());
        return docMethod;
    }

    /**
     * 头部文件
     *
     * @param apiOperation 接口注解
     * @param method       方法
     */
    public void headers(ApiOperation apiOperation, CsapDocMethod method) {
        if (apiOperation == null) {
            return;
        }
        if (ArrayUtil.isEmpty(apiOperation.headers())) {
            return;
        }
        Stream.of(apiOperation.headers())
              .forEach(headers -> method.getMethodHeaders()
                                        .add(CsapDocMethodHeaders.builder()
                                                                 .key(headers.key())
                                                                 .value(headers.value())
                                                                 .description(headers.description())
                                                                 .example(headers.example())
                                                                 .hidden(headers.hidden())
                                                                 .position(headers.position())
                                                                 .required(headers.required()).build()));
        method.getMethodHeaders().addAll(getCsapHeadersProvider().get());
    }

}
