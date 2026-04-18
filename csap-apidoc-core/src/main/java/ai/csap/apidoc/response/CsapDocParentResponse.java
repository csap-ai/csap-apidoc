package ai.csap.apidoc.response;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResource;
import ai.csap.apidoc.properties.CsapApiInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author yangchengfu
 * @description 文档接口返回参数
 * @dataTime 2019年-12月-29日 15:31:00
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(value = "父类文档返回信息")
@Accessors(chain = true)
public class CsapDocParentResponse {
    /**
     * 枚举列表
     */
    private Map<String, List<Map<String, Object>>> enumList;
    /**
     * 全局的api
     */
    private List<CsapDocModelController> globalApiList;
    /**
     * 全局请求参数
     */
    private List<CsapDocModel> globalRequestParam;
    /**
     * 所有 API分组
     */
    private Set<String> groups = new HashSet<>(Lists.newArrayList("default"));

    /**
     * 所有 API版本
     */
    private Set<String> versions = new HashSet<>(Lists.newArrayList("default"));

    /**
     * api的基本信息，用户自定义
     */
    private CsapApiInfo apiInfo;
    /**
     * 当前项目所有的api
     */
    private List<CsapDocParentResponseModel> apiList;

    private List<CsapDocResource> resources;


}
