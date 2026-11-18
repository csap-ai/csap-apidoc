package ai.csap.e2e.rulecheck;

import java.util.List;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

@ApiModel("分页")
public class Page<T> {
    @ApiModelProperty("总数")
    private long total;
    @ApiModelProperty("当前页数据")
    private List<T> rows;

    public Page() {
    }

    public Page(long total, List<T> rows) {
        this.total = total;
        this.rows = rows;
    }

    public long getTotal() {
        return total;
    }

    public List<T> getRows() {
        return rows;
    }
}
