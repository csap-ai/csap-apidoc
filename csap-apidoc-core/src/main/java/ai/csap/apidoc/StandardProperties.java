package ai.csap.apidoc;

import java.util.List;
import java.util.Map;

import ai.csap.apidoc.core.ApidocOptional;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.properties.CsapApiInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @Author ycf
 * @Date 2021/10/11 5:19 下午
 * @Version 1.0
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StandardProperties {
    /**
     * controller map
     * key is className value is controller info
     */
    private Map<String, CsapDocModelController> controllerMap;
    /**
     * 方法map key1 is className
     * key2 is methodName
     */
    private Map<String, Map<String, CsapDocMethod>> methodMap;
    /**
     * /**
     * key1 is controller className
     * key2 is controller on method
     * value2 is request and response include field
     */
    private Map<String, Map<String, ParamGroupMethodProperty>> methodFieldMap;
    /**
     * 所有参数bean map key1 is modelclassName
     */
    private Map<String, CsapDocModel> paramMap;

    /**
     * 枚举 map key1 is className
     */
    private Map<String, List<Map<String, Object>>> enumMap;

    /**
     * 接口信息
     */
    private CsapApiInfo apiInfo;

    public ApidocOptional<StandardProperties> optional() {
        return ApidocOptional.of(this);
    }
}
