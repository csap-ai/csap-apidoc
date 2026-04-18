package ai.csap.apidoc.devtools.model.api;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Response;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2020年-03月-13日 14:13:00
 **/
@ApiModel(value = "数据类型")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataType {
    /**
     * 类型
     */
    @ApiModelProperty(value = "数据库类型", groups = {
            @Group(value = "getDataType", response = @Response(include = true, required = true)),
            @Group(value = "getTableInfoList", response = @Response(include = true, required = true)),
            @Group(value = "getFields", response = @Response(include = true, required = true))
    })
    private String type;


    @ApiModelProperty(value = "数据类型", groups = {
            @Group(value = "getDataType", response = @Response(include = true, required = true)),
            @Group(value = "getTableInfoList", response = @Response(include = true, required = true)),
            @Group(value = "getFields", response = @Response(include = true, required = true))
    })
    private String pkg;
    /**
     * 是否基本数据类型
     */
    @ApiModelProperty(value = "数据类型", groups = {
            @Group(value = "getDataType", response = @Response(include = true, required = true)),
            @Group(value = "getTableInfoList", response = @Response(include = true, required = true)),
            @Group(value = "getFields", response = @Response(include = true, required = true))
    })
    private boolean basicData;

    public String getPkg() {
        if (StrUtil.isEmpty(pkg)) {
            basicData = true;
        }
        return basicData ? type : pkg;
    }
}
