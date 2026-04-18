package ai.csap.apidoc.model;

import java.util.List;
import java.util.Set;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.type.ModelType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description javaBean model
 * @dataTime 2019年-12月-29日 15:07:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel
public class CsapDocModel {
    /**
     * Bean名称描述
     */
    private String value;
    /**
     * 实际名称
     */
    private String name;
    /**
     * 键名称
     */
    private String keyName;

    /**
     * 详细描述
     */
    private String description;
    /**
     * 对象类型
     */
    private ModelType modelType;
    /**
     * 当前Model分组
     */
    private Set<String> group;
    /**
     * 当前Model版本
     */
    private Set<String> version;
    /**
     * 参数类型
     */
    private ParamType paramType = ParamType.DEFAULT;
    /**
     * 参数属性列表
     */
    private List<CsapDocParameter> parameters;
    /**
     * 是否强制显示
     */
    private Boolean force;
    /**
     * 是否全局参数
     */
    private Boolean global;
    /**
     * 方法参数名称
     */
    private String methodParamName;
}
