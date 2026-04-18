package ai.csap.apidoc.devtools.model.api;

import java.util.Collections;
import java.util.List;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.annotation.Request;
import ai.csap.validation.factory.Validate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description 请求字段信息
 * @dataTime 2020年-03月-13日 17:18:00
 **/
@EqualsAndHashCode(callSuper = true)
@ApiModel(value = "请求字段信息")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestField extends Field<RequestField> {

    @ApiModelProperty(value = "是否请求存在", groups = {
            @Group(value = "addRequestGroupParam", request = @Request(required = true))
    })
    private Boolean request = Boolean.TRUE;
    @ApiModelProperty(value = "字段验证信息", forceRep = true)
    private List<Validate.ConstraintValidatorField> validate = Collections.emptyList();
    @ApiModelProperty(value = "请求参数类型", groups = {
            @Group(value = "addRequestParam", request = @Request(required = true)),
            @Group(value = "addRequestGroupParam", request = @Request(required = true))
    })
    @NotNull(message = "请求参数类型")
    private ParamType paramType;
    /**
     * 是否点击了验证（只有点击了验证才表示重新初始化验证数据）
     */
    private Boolean clickValidate = Boolean.FALSE;

    @ApiModelProperty(value = "参数泛型")
    private List<GenericParam> genericParams;
}
