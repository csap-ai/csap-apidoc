package ai.csap.apidoc.response;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMethod;

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
public class CsapDocMethodResponse {

    private List<MethodHeadersResponseModel> headers;

    private List<MethodRequestModel> request;

    private List<MethodRequestModel> response;
    /**
     * 描述
     */
    private String description;

    private String paramType;

    private String title;
    /**
     * 标签
     */
    private String[] tags;
    private String method;
    /**
     * 请求的类型
     *
     * @return
     */
    private List<RequestMethod> methods;
    private String patch;


}
