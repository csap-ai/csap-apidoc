package ai.csap.apidoc.model;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.annotation.Protocols;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description 文档 controller 信息
 * @dataTime 2019年-12月-28日 17:44:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CsapDocModelController {
    /**
     * 描述名称，简称
     */
    private String value;
    /**
     * 具体名称
     */
    private String name;
    /**
     * 简称
     */
    private String simpleName;
    /**
     * 详细描述
     */
    private String description;
    /**
     * 位置
     */
    private int position;
    /**
     * url路径
     */
    private String[] path;
    /**
     * 标签
     */
    private String[] tags;
    /**
     * 筛选条件
     */
    private Set<String> search;
    /**
     * 隐藏的方法
     */
    private List<String> hiddenMethod;
    /**
     * 展示的方法
     */
    private List<String> showMethod;
    /**
     * 当前类的所有API分组
     */
    private Set<String> group;
    /**
     * 所有 API版本
     */
    private Set<String> version;
    /**
     * api状态
     */
    private ApiStatus status;
    /**
     * 协议
     */
    private Protocols protocols;
    /**
     * 当前controller下的所有方法
     */
    private List<CsapDocMethod> methodList;
    /**
     * 当前所有方法名称集合
     */
    private Set<String> methods = ConcurrentHashMap.newKeySet();
    /**
     * 开发模式使用
     */
    private transient Boolean devTools;
    /**
     * 是否隐藏
     */
    private Boolean hidden;
}
