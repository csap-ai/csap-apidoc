package ai.csap.apidoc.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangchengfu
 * @description java Enum model
 * @dataTime 2019年-12月-29日 15:07:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CsapDocEnum {
    /**
     * 枚举code类描述
     */
    private String value;
    /**
     * 名称
     */
    private String name;
    /**
     * 枚举列表
     */
    private List<CsapDocEnumCode> enumList;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CsapDocEnumCode {
        /**
         * 名称
         */
        private String name;
        /**
         * 编码
         */
        private String code;
        /**
         * 描述
         */
        private String descr;
    }
}
