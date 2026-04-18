package ai.csap.apidoc.response;

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
public class CsapDocMethodModel {
    /**
     * 名称描述
     */
    private String title;
    /**
     * 包含类名和方法名称 标识
     */
    private String key;
    /**
     * 实际名称
     */
    private Boolean isLeaf;

    private String method;

    private String type;

    private String path;


}
