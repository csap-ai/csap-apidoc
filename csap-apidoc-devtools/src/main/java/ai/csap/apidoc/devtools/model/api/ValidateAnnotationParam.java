package ai.csap.apidoc.devtools.model.api;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;

import cn.hutool.core.util.StrUtil;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description 验证注解
 * @dataTime 2020年-03月-13日 15:49:00
 **/

@ApiModel("验证注解注解")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ValidateAnnotationParam {

    @ApiModelProperty(value = "编码", example = "200", groups = {
            @Group(value = "addApiMethod", request = @Request),
            @Group(value = "addRequestParam", request = @Request)
    })
    private String code;

    @ApiModelProperty(value = "描述", example = "格式不正确", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true)),
            @Group(value = "addApiValidate", request = @Request(required = true))
    })
    @NotEmpty(message = "描述信息为传")
    private String message;

    @ApiModelProperty(value = "注解包名", example = "jakarta.validation.constraints.NotEmpty", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true)),
            @Group(value = "addRequestParam", request = @Request(required = true)),
            @Group(value = "addApiValidate", request = @Request(required = true))
    })
    @NotEmpty(message = "注解包名必传")
    private String packageName;


    private String simpleName;

    public String getSimpleName() {
        if (StrUtil.isNotEmpty(packageName)) {
            String[] str = packageName.split("\\.");
            simpleName = str[str.length - 1];
        }
        return simpleName;
    }
}
