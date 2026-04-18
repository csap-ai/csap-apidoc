package ai.csap.apidoc.devtools.model;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;

import ai.csap.apidoc.annotation.ApiModelProperty;

import cn.hutool.core.collection.CollectionUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取字段的过滤
 *
 * @author yangchengfu
 * @dataTime 2021年-05月-17日 18:55:00
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GetFieldExcled {
    @ApiModelProperty(value = "model类名称", forceReq = true)
    private String className;
    private Type aClass;

    @ApiModelProperty(value = "需要过滤的字段名称", forceReq = true)
    private Set<String> excledFields;

    private String appendName = "";

    /**
     * Controller类名，用于从方法中获取泛型类型
     */
    @ApiModelProperty(value = "Controller类名，用于从方法中获取泛型类型")
    private String controllerClassName;

    /**
     * 方法名，用于从方法中获取泛型类型
     */
    @ApiModelProperty(value = "方法名，用于从方法中获取泛型类型")
    private String methodName;

    /**
     * 参数索引，用于从方法的参数中获取泛型类型
     * 如果为负数（如-1），表示获取返回值类型
     */
    @ApiModelProperty(value = "参数索引，用于从方法的参数中获取泛型类型。如果为-1，表示获取返回值类型")
    private Integer parameterIndex;

    public Set<String> getExcledFields() {
        return CollectionUtil.isEmpty(excledFields) ? Collections.emptySet() : excledFields;
    }

}
