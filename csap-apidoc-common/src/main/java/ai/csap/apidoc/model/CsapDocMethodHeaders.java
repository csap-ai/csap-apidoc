package ai.csap.apidoc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description
 * @dataTime 2020年-02月-28日 23:53:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CsapDocMethodHeaders {

    /**
     * 键
     */
    private String key;
    /**
     * 值
     */
    private String value;

    /**
     * 是否必传
     */
    private Boolean required;

    /**
     * 位置
     */
    private int position;

    /**
     * 是否隐藏
     */
    private Boolean hidden;
    /**
     * 示例.
     */
    private String example;
    /**
     * 描述
     *
     */
    private String description;
}
