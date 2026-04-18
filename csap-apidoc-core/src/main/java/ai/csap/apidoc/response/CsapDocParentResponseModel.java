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
public class CsapDocParentResponseModel {
    /**
     * 描述名称，简称
     */
    private String title;
    private String key;
    /**
     * 当前controller下的所有方法
     */
    private List<CsapDocMethodModel> children;
}
