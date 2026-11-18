package ai.csap.e2e.rulecheck;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

@ApiModel("条目")
public class Item {
    @ApiModelProperty("ID")
    private Long id;
    @ApiModelProperty("名称")
    private String name;

    public Item() {
    }

    public Item(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
