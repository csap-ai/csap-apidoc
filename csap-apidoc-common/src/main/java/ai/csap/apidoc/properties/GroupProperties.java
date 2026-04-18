package ai.csap.apidoc.properties;

import ai.csap.apidoc.annotation.ParamType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author ycf
 * @Date 2023/5/9 13:25
 * @Version 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupProperties {
    /**
     * 标识
     */
    private String value;
    /**
     * 是否必须
     */
    private Boolean required;
    /**
     * 是否包含
     */
    private Boolean include;

    /**
     * 参数类型
     */
    private ParamType paramType;


}
