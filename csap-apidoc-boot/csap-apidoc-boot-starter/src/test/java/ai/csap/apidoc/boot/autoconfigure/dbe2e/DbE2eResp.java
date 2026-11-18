package ai.csap.apidoc.boot.autoconfigure.dbe2e;

import ai.csap.apidoc.annotation.ApiModelProperty;
import lombok.Data;

@Data
public class DbE2eResp {
    @ApiModelProperty(value = "编号")
    private Long id;

    @ApiModelProperty(value = "名称")
    private String name;
}
