package ai.csap.apidoc.devtools.model.api;

import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * @author yangchengfu
 * @description 基本参数类型
 * @dataTime 2020年-03月-14日 16:06:00
 **/
@Data
public class BaseFieldParam {
    @ApiModelProperty(value = "参数包名", example = "java.lang.String", groups = {
            @Group(value = "addApiMethod", request = @Request),
            @Group(value = "addRequestGroupParam", request = @Request),
            @Group(value = "addResponseGroupParam", request = @Request),
            @Group(value = "addApiValidate", request = @Request)
    })
    @NotEmpty(message = "包名称必传")
    protected String packageName;
    @ApiModelProperty(value = "原始class名称")
    private String finalClassName;
    @ApiModelProperty(value = "参数实际名称", example = "String", groups = {
            @Group(value = "addApiMethod", request = @Request(value = true, required = true))
    })
    @NotEmpty(message = "名称必传")
    protected String simpleName;
    @ApiModelProperty(value = "字段描述", example = "描述", description = "字段描述")
    protected String description;
    @ApiModelProperty(value = "字段备注", example = "备注", description = "字段备注")
    protected String value;
    @ApiModelProperty(value = "唯一标识", description = "唯一标识", forceRep = true)
    private String id;
    @ApiModelProperty(value = "唯一标识", description = "唯一标识", forceRep = true)
    private String key;
}
