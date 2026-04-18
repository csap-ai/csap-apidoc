package ai.csap.apidoc.devtools.model.api;

import static ai.csap.apidoc.util.IValidate.EMPTY;

import java.util.List;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import ai.csap.apidoc.annotation.Group;
import ai.csap.apidoc.annotation.Request;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description 返回参数
 * @dataTime 2020年-03月-13日 15:51:00
 **/

@ApiModel("返回参数")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseParam extends BaseParam {

    @ApiModelProperty(value = "需要的字段", groups = {
            @Group(value = "addApiMethod", request = @Request(required = true)),
            @Group(value = "addResponseGroupParam", request = @Request())
    })
    private List<ResponseField> fields;

    @ApiModelProperty(value = "参数泛型")
    private List<GenericParam> genericParams;
    @ApiModelProperty("内置添加名称")
    private String appendName = EMPTY;

    @Override
    public String getSimpleName() {
        if (CollectionUtil.isNotEmpty(genericParams)) {
            simpleName = simpleName + "<" + genericParams.get(0).getSimpleName() + ">";
        }
        return simpleName;
    }

    public String getAppendName() {
        return StrUtil.isEmpty(appendName) || appendName.endsWith(StrUtil.DOT) ? appendName : appendName + StrUtil.DOT;
    }

    public ResponseParam(String packageName, String simpleName) {
        this.packageName = packageName;
        this.simpleName = simpleName;
    }

}
