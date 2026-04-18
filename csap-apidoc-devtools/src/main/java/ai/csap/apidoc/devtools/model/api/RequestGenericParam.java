package ai.csap.apidoc.devtools.model.api;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

import lombok.Data;

/**
 * @author yangchengfu
 * @description 请求参数泛型
 * @dataTime 2020年-03月-13日 18:15:00
 **/
@ApiModel(value = "请求泛型相关信息")
@Data
public class RequestGenericParam extends GenericParam {
    @ApiModelProperty(value = "泛型请求的参数")
    private RequestParam requestParam;
}
