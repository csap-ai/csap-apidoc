package ai.csap.apidoc.test.model;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;
import ai.csap.apidoc.annotation.Response;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * @author yangchengfu
 * @description
 * @dataTime 2019年-12月-28日 18:50:00
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ApiModel(value = "测试Listmodel", description = "一个测试Listmodel")
public class TestListModel {
    @ApiModelProperty(value = "密码", groups = {
            @Group(value = "query"),
            @Group(value = "authorization", response = @Response, request = @Request(required = true))
    })
    @NotNull
    private String password;
    @ApiModelProperty(value = "密码", groups = {
            @Group(value = "query"),
            @Group(value = "authorization", response = @Response, request = @Request(required = true))
    })
    @NotNull
    private String password1;
    @ApiModelProperty(value = "密码", groups = {
            @Group(value = "query"),
            @Group(value = "authorization", request = @Request(required = true))
    })
    private String password2;
}
