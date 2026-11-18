package ai.csap.e2e.rulecheck;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

@ApiModel("统一返回")
public class Result<T> {
    @ApiModelProperty("状态码")
    private String code = "0";
    @ApiModelProperty("数据")
    private T data;

    public Result() {
    }

    public Result(T data) {
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public T getData() {
        return data;
    }
}
