package ai.csap.apidoc.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description 文档 controller 信息
 * @dataTime 2019年-12月-28日 17:44:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MethodResponseModel {
    /**
     * 名称描述
     */
    private String key;
    /**
     * 包含类名和方法名称 标识
     */
    private String name;
    /**
     * 实际名称
     */
    private Boolean required;

    private String value;

    private String dataType;

    private String defaultValue;

    private String example;

    private String description;

    private List<MethodResponseModel> children;


}
