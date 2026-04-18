package ai.csap.apidoc.web;

import static ai.csap.apidoc.util.ApidocUtils.apiPath;
import static ai.csap.apidoc.util.ApidocUtils.formatPath;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import ai.csap.apidoc.annotation.ParamType;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.GlobalCsapResponseParamProvider;
import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.response.CsapDocMethodModel;
import ai.csap.apidoc.response.CsapDocMethodResponse;
import ai.csap.apidoc.response.CsapDocParentResponse;
import ai.csap.apidoc.response.CsapDocParentResponseModel;
import ai.csap.apidoc.response.MethodHeadersResponseModel;
import ai.csap.apidoc.response.MethodRequestModel;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.service.standard.PostmanConverterService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;

/**
 * API文档核心接口控制器
 * 提供API文档的查询、导出等核心功能
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>获取完整的API文档数据</li>
 *   <li>按类名或方法名筛选文档</li>
 *   <li>获取接口详情信息</li>
 *   <li>导出Postman格式文档</li>
 *   <li>提供树形结构的父子关系接口</li>
 * </ul>
 *
 * <p>访问路径：</p>
 * <ul>
 *   <li>GET /csap/apidoc - 获取所有API文档</li>
 *   <li>GET /csap/apidoc/{tableName} - 获取指定Controller的文档</li>
 *   <li>GET /csap/apidoc/method - 获取指定方法的详情</li>
 *   <li>GET /csap/apidoc/parent - 获取树形结构文档</li>
 *   <li>GET /csap/openapi/postman - 导出Postman格式</li>
 * </ul>
 *
 * @author yangchengfu
 * @dataTime 2020年-01月-02日 17:09:00
 **/
@RestController
@RequestMapping("/csap")
public class CsapDocController {
    /**
     * Spring应用上下文
     * 用于动态获取Bean实例
     */
    private final ApplicationContext applicationContext;

    /**
     * Postman格式转换服务
     * 用于将API文档转换为Postman Collection格式
     */
    private final PostmanConverterService postmanConverterService;

    /**
     * 构造函数，注入依赖的服务
     *
     * @param applicationContext      Spring应用上下文
     * @param postmanConverterService Postman转换服务
     */
    public CsapDocController(ApplicationContext applicationContext, PostmanConverterService postmanConverterService) {
        this.applicationContext = applicationContext;
        this.postmanConverterService = postmanConverterService;
    }

    /**
     * 获取全局返回参数提供者
     * 用于提供通用的响应参数配置
     *
     * @return 全局响应参数提供者实例
     */
    public GlobalCsapResponseParamProvider responseParamProvider() {
        return applicationContext.getBean(GlobalCsapResponseParamProvider.class);
    }

    /**
     * 获取完整的API文档
     * 返回系统中所有Controller的接口文档信息
     *
     * <p>功能特性：</p>
     * <ul>
     *   <li>支持缓存机制，避免重复扫描</li>
     *   <li>可通过flush参数强制刷新缓存</li>
     *   <li>包含Controller、方法、参数、枚举等完整信息</li>
     * </ul>
     *
     * @param flush 是否刷新缓存，true表示重新扫描生成文档，false表示使用缓存
     * @return 包含完整API文档数据的响应对象
     */
    @GetMapping("/apidoc")
    public ApidocResult<CsapDocResponse> apidoc(@RequestParam(required = false, defaultValue = "false") Boolean flush) {
        return ApidocResult.success(ApidocContext.cmd(flush, false));
    }

    /**
     * 根据Controller名称和方法名过滤API列表
     *
     * <p>过滤逻辑：</p>
     * <ul>
     *   <li>按 tableName 过滤 Controller 列表</li>
     *   <li>如果提供 key 参数，进一步过滤方法列表</li>
     *   <li>返回过滤后的响应对象</li>
     * </ul>
     *
     * @param response  原始API文档响应对象
     * @param tableName Controller的完全限定名
     * @param key       可选，方法的完全限定名
     * @return 过滤后的响应对象
     */
    private CsapDocResponse apiList(CsapDocResponse response, String tableName, String key) {
        // 空值安全检查
        if (response == null || CollectionUtil.isEmpty(response.getApiList())) {
            return response;
        }
        // 如果提供了key参数，进一步过滤每个Controller的methodList
        if (StrUtil.isNotBlank(key)) {
            CsapDocResponse docResponse = BeanUtil.copyProperties(response, CsapDocResponse.class);
            // 根据tableName过滤apiList
            List<CsapDocModelController> filteredApiList = response.getApiList().stream()
                                                                   .filter(controller -> controller.getName()
                                                                                                   .equals(tableName))
                                                                   .peek(i -> {
                                                                       List<CsapDocMethod> filteredMethods = i
                                                                               .getMethodList()
                                                                               .stream()
                                                                               .filter(method -> method.getKey()
                                                                                                       .equals(key))
                                                                               .collect(Collectors.toList());
                                                                       i.setMethodList(filteredMethods);
                                                                   })
                                                                   .collect(Collectors.toList());
            docResponse.setApiList(filteredApiList);
            return docResponse;
        }
        return response;
    }

    /**
     * 获取指定Controller的API文档
     * 可进一步筛选到具体的方法
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>查看单个Controller的所有接口</li>
     *   <li>查看Controller中的特定接口方法</li>
     * </ul>
     *
     * @param tableName Controller的完全限定名，如"com.example.UserController"
     * @param key       可选，方法的完全限定名，如"com.example.UserController.getUser"
     * @return 包含指定Controller或方法的文档数据
     */
    @GetMapping("/apidoc/{tableName}")
    public ApidocResult<CsapDocResponse> apidocName(@PathVariable("tableName") String tableName,
                                                    @RequestParam(required = false) String key) {
        return ApidocResult.success(apiList(ApidocContext.cmd(tableName, key, false), tableName, key));
    }

    /**
     * 获取树形结构的API文档
     * 将Controller和方法组织成父子关系的树形结构
     *
     * <p>数据结构：</p>
     * <pre>
     * - Controller 1
     *   - Method 1.1
     *   - Method 1.2
     * - Controller 2
     *   - Method 2.1
     * </pre>
     *
     * <p>适用场景：前端树形控件展示</p>
     *
     * @return 树形结构的文档数据
     */
    @GetMapping("/apidoc/parent")
    public ApidocResult<CsapDocParentResponse> apiDocParent() {
        CsapDocResponse apiDoc = ApidocContext.cmd(false, true);
        List<CsapDocParentResponseModel> apiList = apiDoc.getApiList().stream()
                                                         .map(item -> CsapDocParentResponseModel.builder()
                                                                                                .title(item.getValue())
                                                                                                .key(item.getName())
                                                                                                .children(setChildren(item.getMethodList(), item
                                                                                                        .getProtocols()
                                                                                                        .name()))
                                                                                                .build())
                                                         .collect(Collectors.toList());
        CsapDocParentResponse response = CsapDocParentResponse.builder()
                                                              .resources(apiDoc.getResources())
                                                              .enumList(apiDoc.getEnumList())
                                                              .apiInfo(apiDoc.getApiInfo())
                                                              .groups(apiDoc.getGroups())
                                                              .versions(apiDoc.getVersions())
                                                              .apiList(apiList)
                                                              .build();
        return ApidocResult.success(response);
    }

    /**
     * 导出Postman格式的API文档
     * 将当前的API文档转换为Postman Collection格式
     *
     * <p>功能说明：</p>
     * <ul>
     *   <li>自动生成Postman可导入的JSON格式</li>
     *   <li>包含完整的请求信息（URL、方法、参数、请求头等）</li>
     *   <li>可直接导入Postman进行接口测试</li>
     * </ul>
     *
     * <p>使用方法：</p>
     * <ol>
     *   <li>访问此接口获取JSON内容</li>
     *   <li>复制JSON内容到文件</li>
     *   <li>在Postman中选择"Import"导入文件</li>
     * </ol>
     *
     * @return Postman Collection格式的JSON字符串
     */
    @GetMapping("/openapi/postman")
    public String postManJson() {
        ApidocResult<CsapDocResponse> apidoc = apidoc(false);
        return postmanConverterService.convertToPostmanCollection(apidoc.getData());
    }

    /**
     * 获取接口方法的详细信息
     * 包含请求参数、返回值、请求头等完整信息
     *
     * <p>返回信息包括：</p>
     * <ul>
     *   <li>方法基本信息：名称、描述、路径、HTTP方法</li>
     *   <li>请求信息：参数列表、参数类型、验证规则</li>
     *   <li>响应信息：返回值结构</li>
     *   <li>请求头信息：必需的请求头配置</li>
     * </ul>
     *
     * @param name Controller的完全限定名，如"com.example.UserController"
     * @param key  方法的完全限定名，如"com.example.UserController.getUser"
     * @return 包含方法完整信息的响应对象
     */
    @GetMapping("/apidoc/method")
    public ApidocResult<CsapDocMethodResponse> apiDocMethod(@RequestParam String name, @RequestParam String key) {
        return (ApidocContext.cmd(name, key, false))
                .getApiList()
                .stream()
                .filter(item -> item.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getMethodList()
                .stream()
                .filter(item -> item.getKey().equals(key))
                .findFirst()
                .map(i -> CsapDocMethodResponse.builder()
                                               .headers(BeanUtil.copyToList(i.getMethodHeaders(), MethodHeadersResponseModel.class))
                                               .patch(formatPath(apiPath(i.getApiPath())) + formatPath(ArrayUtil.isEmpty(i.getPaths()) ? "" : i.getPaths()[0]))
                                               .method(i.getMethods().get(0).name())
                                               .title(i.getValue()).tags(i.getTags())
                                               .methods(i.getMethods())
                                               .description(i.getDescription())
                                               .paramType(i.getParamType().name())
                                               .request(setChildParam(i.getRequest()))
                                               .response(setResponseChildParam(i.getResponse()))
                                               .build())
                .map(ApidocResult::success).orElseThrow();

    }

    public List<CsapDocMethodModel> setChildren(List<CsapDocMethod> methodList, String type) {
        return methodList.stream()
                         .map(item -> CsapDocMethodModel.builder()
                                                        .title(item.getValue())
                                                        .key(item.getKey())
                                                        .isLeaf(Boolean.TRUE)
                                                        .method(item.getMethods().get(0).name())
                                                        .path(formatPath(apiPath(item.getApiPath())) + formatPath(ArrayUtil.isEmpty(item.getPaths()) ? "" : item.getPaths()[0]))
                                                        .type(type).build())
                         .collect(Collectors.toList());
    }

    /**
     * 独立处理返回参数
     *
     * @param request 参数
     * @return 结果
     */
    public List<MethodRequestModel> setResponseChildParam(List<CsapDocModel> request) {
        return setChildParam(request);
    }

    public List<MethodRequestModel> setChildParam(List<CsapDocModel> request) {
        if (CollectionUtil.isEmpty(request)) {
            return Collections.emptyList();
        }
        return request.stream()
                      .map(CsapDocModel::getParameters)
                      .map(this::params)
                      .flatMap(Collection::stream)
                      .collect(Collectors.toList());
    }

    private List<MethodRequestModel> params(List<CsapDocParameter> parameters) {
        if (CollectionUtil.isEmpty(parameters)) {
            return Collections.emptyList();
        }
        return parameters.stream()
                         .map(item -> MethodRequestModel.builder()
                                                        .key(item.getKey()).dataType(item.getDataType())
                                                        .required(item.getRequired())
                                                        .defaultValue(item.getDefaultValue())
                                                        .name(item.getName())
                                                        .value(item.getValue())
                                                        .paramType(Objects.isNull(item.getParamType()) ? ParamType.DEFAULT.name() : item
                                                                .getParamType().name())
                                                        .example(item.getExample())
                                                        .modelType(item.getModelType())
                                                        .description(item.getDescription())
                                                        .extendDescr(item.getExtendDescr())
                                                        .validate(item.getValidate())
                                                        .children(params(item.getParameters())).build())
                         .collect(Collectors.toList());
    }
}
