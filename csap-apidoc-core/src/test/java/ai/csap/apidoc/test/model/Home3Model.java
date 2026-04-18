package ai.csap.apidoc.test.model;

import java.util.List;

import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Response;

import lombok.Data;

/**
 * @author yangchengfu
 * @dataTime 2021年-05月-23日 22:26:00
 **/
@Data
public class Home3Model {
    @ApiModelProperty(groups = {
            @Group(response = @Response(required = true), value = "home3")}, value = "固废交易信息")
    private List<SolidWasteModel> solidWastel;

}
