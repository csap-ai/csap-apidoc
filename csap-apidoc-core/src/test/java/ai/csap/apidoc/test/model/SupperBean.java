package ai.csap.apidoc.test.model;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;
import ai.csap.apidoc.annotation.Response;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * @author yangchengfu
 * @description
 * @dataTime 2021年-01月-25日 19:40:00
 **/
@ApiModel(value = "测试supper Bean", description = "一个测试Bean")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupperBean {
    @ApiModelProperty(value = "名称", groups = {
            @Group(value = "query"),
            @Group(value = "authorization", response = @Response, request = @Request(required = true))
    })
    @NotNull
    private String name2;
}
