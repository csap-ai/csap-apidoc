package ai.csap.example.model;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * 泛型基类请求 - 用于测试继承泛型父类的场景
 *
 * @param <T> 泛型数据类型
 * @author CSAP Team
 */
@Data
@ApiModel(description = "泛型基类请求")
public class BaseRequest<T> {

    @ApiModelProperty(value = "泛型数据", forceReq = true)
    private T data;

    @ApiModelProperty(value = "泛型数据列表")
    private List<T> dataList;

    @ApiModelProperty(value = "请求ID", example = "REQ-001")
    private String requestId;

    @ApiModelProperty(value = "时间戳", example = "1704096000000")
    private Long timestamp;

    @ApiModelProperty(value = "操作人", example = "admin")
    private String operator;
}

