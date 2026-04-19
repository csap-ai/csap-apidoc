package ai.csap.apidoc.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.RequestMethod;

import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.annotation.ParamType;

import cn.hutool.core.collection.CollectionUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API文档方法信息模型
 * 表示Controller中的一个接口方法的完整文档信息
 *
 * <p>此模型包含：</p>
 * <ul>
 *   <li>方法基本信息：名称、描述、路径等</li>
 *   <li>请求信息：HTTP方法、参数类型、请求体结构</li>
 *   <li>响应信息：返回值结构</li>
 *   <li>元数据：分组、版本、标签等</li>
 * </ul>
 *
 * @author yangchengfu
 * @dataTime 2019年-12月-28日 17:44:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CsapDocMethod {
    /**
     * 方法的显示名称或标题
     * 用于在文档界面中展示，来自@ApiOperation注解的value属性
     */
    private String value;

    /**
     * 方法的唯一标识符
     * 格式：完整类名.方法名，如"com.example.UserController.getUserById"
     * 用于在系统中唯一标识一个接口方法
     */
    private String key;

    /**
     * 方法所属类的完全限定名
     * 如"com.example.UserController"
     */
    private String className;

    /**
     * 类的简单名称（不含包名）
     * 如"UserController"
     */
    private String simpleName;

    /**
     * 方法的实际名称
     * 如"getUserById"
     */
    private String name;

    /**
     * 方法级别的URL路径数组
     * 来自@RequestMapping、@GetMapping等注解的path或value属性
     * 可能包含路径变量，如["/user/{id}"]
     */
    private String[] paths;

    /**
     * Controller级别的URL路径数组
     * 来自Controller类上的@RequestMapping注解
     * 与paths组合形成完整的URL路径
     */
    private String[] apiPath;

    /**
     * 方法的详细描述
     * 用于说明接口的功能和用途
     */
    private String description;

    /**
     * 支持的HTTP请求方法列表
     * 如GET、POST、PUT、DELETE等
     * 一个接口可能支持多种HTTP方法
     */
    private List<RequestMethod> methods;

    /**
     * 搜索关键词集合
     * 用于在文档界面中进行全文搜索
     * 包含方法名、描述、标签等信息
     */
    private Set<String> search;

    /**
     * 方法标签数组
     * 用于对接口进行分类和标记
     * 便于文档的组织和查找
     */
    private String[] tags;

    /**
     * 参数类型名称集合
     * 包含所有参数的ParamType枚举值
     * 如["QUERY", "PATH", "BODY"]
     */
    private Set<String> paramTypes;

    /**
     * 默认参数类型
     * 当参数未明确指定类型时使用此默认值
     */
    private ParamType paramType = ParamType.DEFAULT;

    /**
     * 接口状态
     * 如稳定、测试、废弃等
     * 用于标识接口的生命周期阶段
     */
    private ApiStatus status;

    /**
     * 请求参数模型列表
     * 描述接口所需的所有输入参数的结构
     * 包括路径参数、查询参数、请求体等
     */
    private List<CsapDocModel> request;

    /**
     * 响应数据模型列表
     * 描述接口返回数据的结构
     */
    private List<CsapDocModel> response;

    /**
     * HTTP请求头列表
     * 定义接口需要的特殊请求头
     * 如Content-Type、Authorization等
     */
    private List<CsapDocMethodHeaders> methodHeaders;

    /**
     * 方法参数名称列表
     * 按照方法定义的顺序存储参数名
     * 用于将参数类型与参数名对应
     */
    private List<String> paramNames;

    /**
     * 接口所属的API分组集合
     * 用于多版本或多模块的接口管理
     * 一个接口可以属于多个分组
     */
    private Set<String> group;

    /**
     * 接口的版本标识集合
     * 用于API版本控制
     * 一个接口可以存在于多个版本中
     */
    private Set<String> version;

    /**
     * 是否在文档中隐藏此接口
     * true表示不在公开文档中显示
     * 通常用于内部接口或开发中的接口
     */
    private Boolean hidden;

    /**
     * try-it-out 全局请求头建议（M7）。
     * 由 scanner 从 {@link ai.csap.apidoc.annotation.DocGlobalHeader}
     * 在 method &gt; class &gt; package 三级合并而来，按 name 去重，高优先级覆盖。
     * 仅供 {@code csap-apidoc-ui} 自动预填，不参与后端实际请求处理。
     */
    private List<CsapDocGlobalHeaderHint> globalHeaderHints;

    /**
     * try-it-out 认证建议（M7）。
     * 由 scanner 从 {@link ai.csap.apidoc.annotation.DocAuth}
     * 在 method &gt; class &gt; package 三级中取最高优先级单值。
     */
    private CsapDocAuthHint authHint;

    /**
     * 添加请求参数模型到现有列表
     * 支持动态扩展请求参数
     *
     * @param request 待添加的请求参数模型列表
     */
    public void addRequest(List<CsapDocModel> request) {
        if (CollectionUtil.isEmpty(this.request)) {
            this.request = new ArrayList<>();
        }
        if (CollectionUtil.isNotEmpty(request)) {
            this.request.addAll(request);
        }
    }


}
