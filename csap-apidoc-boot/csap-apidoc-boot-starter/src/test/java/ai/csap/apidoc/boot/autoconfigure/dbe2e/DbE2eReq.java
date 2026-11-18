package ai.csap.apidoc.boot.autoconfigure.dbe2e;


import ai.csap.apidoc.annotation.ApiModelProperty;
import lombok.Data;

@Data
public class DbE2eReq {
    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "年龄")
    private Integer age;
}
