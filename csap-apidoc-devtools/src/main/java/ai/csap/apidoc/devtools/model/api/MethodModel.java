package ai.csap.apidoc.devtools.model.api;

import java.util.List;

import org.springframework.http.HttpMethod;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Description;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.annotation.Request;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 方法公用的模型
 *
 * @author yangchengfu
 * @date 2020/5/19 11:17 下午
 **/
@ApiModel("操作api方法请求参数")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Description(value = "操作api请求Model", author = "yangchengfu", dataTime = "2020年-02月-06日 18:14:00")
public class MethodModel {

    @ApiModelProperty(value = "指定添加的类名", example = "ai.csap.apidoc.devtools.model.AddApiMethodRequest", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true, paramType = ParamType.BODY)),
            @Group(value = "hiddenMethod", request = @Request(required = true))
    })
    @NotEmpty(message = "指定添加的类名必传")
    private String className;
    @ApiModelProperty(value = "是否隐藏", groups = {
            @Group(value = "hiddenMethod", request = @Request(required = true))
    })
    private Boolean hidden;
    @ApiModelProperty(value = "方法的作用域", example = "private", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true, paramType = ParamType.BODY)),
    })
    private String scopeType = "public";

    @ApiModelProperty(value = "方法名称", example = "add", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true)),
            @Group(value = "addRequestGroupParam", request = @Request(required = true)),
            @Group(value = "addResponseGroupParam", request = @Request(required = true)),
            @Group(value = "hiddenMethod", request = @Request(required = true))

    })
    @NotEmpty(message = "方法名必传")
    private String methodName;
    /**
     * 方法参数名称
     */
    private String methodParamName;
    @ApiModelProperty(value = "方法描述", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true))

    })
    @NotEmpty(message = "描述不能为空")
    private String description;

    @ApiModelProperty(value = "请求类型",
            groups = {@Group(value = "addApiMethod", request = @Request(required = true))}
    )
    @NotEmpty(message = "请求类型不能为空")
    private HttpMethod httpMethod;

    @ApiModelProperty(value = "是否static类型", defaultValue = "false",
            groups = {@Group(value = "addApiMethod", request = @Request())}
    )
    private Boolean isStatic = Boolean.FALSE;

    @ApiModelProperty(value = "是否final类型", defaultValue = "false", groups = {
            @Group(value = "addApiMethod", request = @Request())
    })
    private Boolean isFinal = Boolean.FALSE;

    @ApiModelProperty(value = "是否同步块", defaultValue = "false", groups = {
            @Group(value = "addApiMethod", request = @Request())
    })
    private Boolean isSynchronized = Boolean.FALSE;

    @ApiModelProperty(value = "方法参数", groups = {
            @Group(value = "addApiMethod", request = @Request()),
            @Group(value = "addRequestGroupParam", request = @Request(required = true))

    })
    @NotEmpty(message = "请求参数未传")
    private List<RequestParam> requestParams;

    @ApiModelProperty(value = "方法返回的参数类型", groups = {
            @Group(value = "addApiMethod", request = @Request()),
            @Group(value = "addResponseGroupParam", request = @Request(required = true))
    })
    @NotNull(message = "返回类型未传")
    private ResponseParam returnType;

    @ApiModelProperty(value = "方法返回字符串", groups = {
            @Group(value = "addApiMethod", request = @Request())

    })
    private String returnStr;

    public String getReturnStr() {
        if (StrUtil.isNotEmpty(returnStr)) {
            if (!returnStr.startsWith("return")) {
                returnStr = "return " + returnStr;
            }
            if (!returnStr.endsWith(";")) {
                returnStr = returnStr + ";";
            }
        }
        return returnStr;
    }


}
