package ai.csap.apidoc.devtools.model.api;

import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;
import ai.csap.apidoc.type.ModelType;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * @author yangchengfu
 * @description 基本参数类型
 * @dataTime 2020年-03月-14日 16:06:00
 **/
@Data
public class BaseParam {
    @ApiModelProperty(value = "参数包名", example = "java.lang.String", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true)),
            @Group(value = "addRequestGroupParam", request = @Request(required = true)),
            @Group(value = "addResponseGroupParam", request = @Request(required = true)),
            @Group(value = "addApiValidate", request = @Request(required = true))
    })
    @NotEmpty(message = "包名称必传")
    protected String packageName;

    @ApiModelProperty(value = "参数实际名称", example = "String", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true))
    })
    @NotEmpty(message = "名称必传")
    protected String simpleName;
    /**
     * 参数名称(别名)
     */
    protected String methodParamName;
    /**
     * 对象类型
     */
    private ModelType modelType;

    public void setPackageName(String packageName) {
        this.packageName = packageName;
        if (StrUtil.isNotEmpty(packageName) && StrUtil.isEmpty(simpleName)) {
            String[] str = packageName.split("\\.");
            this.simpleName = str[str.length - 1];
        }
    }
}
