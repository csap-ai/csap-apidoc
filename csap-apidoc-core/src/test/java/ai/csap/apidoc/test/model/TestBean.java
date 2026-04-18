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
 * @dataTime 2019年-12月-28日 21:16:00
 **/
@ApiModel(value = "测试Bean", description = "一个测试Bean")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TestBean extends SupperBean {
    @ApiModelProperty(value = "名称", groups = {
            @Group(value = "query"),
            @Group(value = "authorization", response = @Response, request = @Request(required = true))
    })
    @NotNull
    private String name;


}
