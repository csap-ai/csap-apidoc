package ai.csap.apidoc.devtools.model.api;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description 返回字段信息
 * @dataTime 2020年-03月-13日 17:19:00
 **/
@ApiModel(value = "返回字段信息")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseField extends Field<ResponseField> {
    /**
     * 是否包含
     */
    @ApiModelProperty(value = "是否包含", groups = {
            @Group(value = "addResponseGroupParam", request = @Request(required = true))
    })
    private Boolean include = Boolean.FALSE;


}
