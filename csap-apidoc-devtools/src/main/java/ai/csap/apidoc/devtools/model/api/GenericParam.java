package ai.csap.apidoc.devtools.model.api;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

import cn.hutool.core.util.StrUtil;
import lombok.Data;

/**
 * @author yangchengfu
 * @description 泛型信息
 * @dataTime 2020年-03月-13日 17:08:00
 **/
@Data
@ApiModel(value = "泛型信息")
public class GenericParam {
    /**
     * 包名
     */
    @ApiModelProperty(value = "包名")
    private String packageName;
    /**
     * 短名称
     */
    @ApiModelProperty(value = "短名称")
    private String simpleName;

    public String getSimpleName() {
        if (StrUtil.isNotEmpty(packageName)) {
            String[] str = packageName.split("\\.");
            simpleName = str[str.length - 1];
        }
        return simpleName;
    }
}
