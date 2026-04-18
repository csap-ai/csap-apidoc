package ai.csap.apidoc.devtools.model.api;

import java.util.LinkedList;
import java.util.List;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;
import ai.csap.apidoc.annotation.Response;
import ai.csap.apidoc.type.ModelType;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author yangchengfu
 * @description 字段
 * @dataTime 2020年-03月-13日 15:51:00
 **/

@Data
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel("字段")
public class Field<T extends Field<T>> extends BaseFieldParam implements Cloneable {

    @ApiModelProperty(value = "字段名称", example = "name", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true)),
            @Group(value = "getFields", response = @Response(required = true)),
            @Group(value = "addRequestGroupParam", request = @Request(required = true))

    })
    @NotEmpty(message = "字段名称必传")
    protected String name;

    /**
     * 实体类型
     */
    private ModelType modelType;
    /**
     * 请求强制显示，忽略所有group 只要有用到就会显示.
     *
     * @return force request display flag
     */
    protected Boolean forceReq;

    /**
     * 返回强制显示，忽略所有group 只要有用到就会显示.
     *
     * @return force response display flag
     */
    protected Boolean forceRep;

    /**
     * 请求强制忽略 忽略所有group 只要有用到就会忽略 >forceReq
     *
     * @return
     */
    protected Boolean ignoreReq;

    /**
     * 返回强制忽略，忽略所有group 只要有用到就会忽略 > forceRep
     *
     * @return
     */
    protected Boolean ignoreRep;


    @ApiModelProperty(value = "接口分组", example = "group")
    @NotEmpty(message = "分组不能为空")
    protected String[] group;

    @ApiModelProperty(value = "字段名称", example = "1.0")
    @NotEmpty(message = "版本不能为空")
    protected String[] version;

    @ApiModelProperty(value = "数据类型", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true)),
            @Group(value = "getFields", response = @Response(required = true))
    })
    protected String dataType;

    @ApiModelProperty(value = "是否必传", groups = {
            @Group(value = "addRequestGroupParam", request = @Request(required = true)),
            @Group(value = "addResponseGroupParam", request = @Request(required = true))
    })
    protected Boolean required = Boolean.FALSE;


    @ApiModelProperty(value = "是否隐藏", groups = {
            @Group(value = "addRequestGroupParam", request = @Request(required = true))
    })
    protected Boolean hidden = Boolean.FALSE;

    @ApiModelProperty(value = "示例", groups = {
            @Group(value = "addRequestGroupParam", request = @Request(required = true))
    })
    protected String example;


    @ApiModelProperty(value = "默认值", groups = {
            @Group(value = "addRequestGroupParam", request = @Request(required = true))
    })
    protected String defaultValue;

    @ApiModelProperty(value = "参数注解", groups = {
            @Group(value = "addApiMethod", request = @Request),
            @Group(value = "addRequestGroupParam", request = @Request),
            @Group(value = "addApiValidate", request = @Request)
    })
    @NotEmpty(message = "验证注解未传")
    protected List<ValidateAnnotationParam> annotations;

    @ApiModelProperty(value = "子集字段", groups = {
            @Group(value = "getFields", response = @Response(required = true))
    })
    protected List<T> childrenField;
    @ApiModelProperty(value = "子集字段", groups = {
            @Group(value = "addResponseGroupParam", request = @Request(required = true))
    })
    protected List<T> childrenCheck;
    @ApiModelProperty(value = "子集字段", groups = {
            @Group(value = "addResponseGroupParam", request = @Request(required = true))
    })
    protected List<T> parameters;
    @ApiModelProperty(value = "父类字段", groups = {
            @Group(value = "getFields", response = @Response(required = true))
    })
    private Field parent;
    /**
     * 拼接的名称 不包括当前字段名
     */
    private String appendName;

    @Override
    public String getPackageName() {
        if (StrUtil.isEmpty(packageName)) {
            setPackageName(dataType);
        }
        return packageName;
    }

    @Override
    public String getSimpleName() {
        if (StrUtil.isEmpty(simpleName)) {
            String[] string = dataType.split("\\.");
            setSimpleName(string[string.length - 1]);
        }
        return simpleName;
    }

    /**
     * 添加注解
     *
     * @param validateAnnotationParam
     * @return
     */
    public Field addAnnotation(ValidateAnnotationParam validateAnnotationParam) {
        if (annotations == null) {
            annotations = new LinkedList<>();
        }
        annotations.add(validateAnnotationParam);
        return this;
    }

    @Override
    public Field clone() {
        try {
            return (Field) super.clone();
        } catch (Exception e) {
            // Ignore clone exception
            return null;
        }
    }
}
