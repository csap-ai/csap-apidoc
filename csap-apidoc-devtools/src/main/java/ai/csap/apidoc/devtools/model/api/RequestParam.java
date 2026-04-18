package ai.csap.apidoc.devtools.model.api;


import java.util.List;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author ycf
 */
@ApiModel("参数")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestParam extends BaseParam {
    @ApiModelProperty(value = "描述",
            groups = {
                    @Group(value = "addApiMethod", request = @Request(required = true))
            })
    @NotEmpty(message = "描述不能为空")
    private String description;

    @ApiModelProperty(value = "请求需要的字段", groups = {
            @Group(value = "addApiMethod", request = @Request),
            @Group(value = "addRequestGroupParam", request = @Request(required = true)),
            @Group(value = "addApiValidate", request = @Request(required = true))
    })
    @NotEmpty(message = "请求字段不能为空")
    private List<RequestField> fields;

    @ApiModelProperty(value = "参数泛型")
    private List<GenericParam> genericParams;

    public RequestParam(String description, String packageName, String simpleName) {
        this.description = description;
        this.packageName = packageName;
        this.simpleName = simpleName;
    }

}
