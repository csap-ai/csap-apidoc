package ai.csap.apidoc.response;

import java.util.List;
import java.util.Map;

import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.type.ModelType;

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
public class MethodRequestModel {
    /**
     * 名称描述
     */
    private String key;
    /**
     * 包含类名和方法名称 标识
     */
    private String name;
    /**
     * 实际名称
     */
    private Boolean required;
    /**
     * 描述
     */
    private String value;
    /**
     * 参数类型
     */
    private String paramType;
    /**
     * 数据类型
     */
    private String dataType;
    /**
     * 默认值
     */
    private String defaultValue;

    /**
     * 示例
     */
    private String example;
    /**
     * 参数的模型类型
     * 如BASE_DATA（基础数据类型）、OBJECT（对象）、ARRAY（数组）等
     * 用于区分参数的结构类型
     */
    private ModelType modelType;

    /**
     * 描述
     */
    private String description;
    /**
     * 验证信息
     */
    private List<CsapDocParameter.ValidateAttribute> validate;
    /**
     * 扩展信息显示
     */
    private List<Map<String, String>> extendDescr;
    /**
     * 子级字段
     */
    private List<MethodRequestModel> children;


}
