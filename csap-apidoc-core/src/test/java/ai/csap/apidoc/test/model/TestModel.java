package ai.csap.apidoc.test.model;

import java.util.List;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.annotation.Request;
import ai.csap.apidoc.annotation.Response;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2019年-12月-28日 18:49:00
 **/
@Data
@ApiModel(value = "测试model", description = "一个测试model")
public class TestModel<T> {
    @ApiModelProperty(value = "名称", description = "名称", groups = {
            @Group(value = "add", request = @Request(paramType = ParamType.BODY, required = true), response = @Response),
            @Group(value = "authorization", request = @Request(required = true), response = @Response)
    })
    @NotNull
    private String name;

    @ApiModelProperty(value = "年龄", description = "年龄", groups = {
            @Group(value = "add", request = @Request(value = true, paramType = ParamType.BODY, required = true)),
            @Group(value = "update", request = @Request(value = true, paramType = ParamType.BODY, required = true)),
            @Group(value = "queryManagerPage", request = @Request(value = true, paramType = ParamType.QUERY), response = @Response(include = true))
    }
    )
    @NotNull
    private int age;
    @ApiModelProperty(value = "集合列表", groups = {
            @Group(value = "query", response = @Response),
            @Group(value = "authorization", response = @Response, request = @Request(required = true))
    })
    @NotEmpty
    private List<TestListModel> listModels;

    /**
     * 测试的bean
     */
    @ApiModelProperty(value = "测试bean", groups = {
            @Group(value = "query", response = @Response),
            @Group(value = "authorization", response = @Response, request = @Request(required = true))
    })
    @NotNull(message = "测试对象未传")
    private T testBean;
}
