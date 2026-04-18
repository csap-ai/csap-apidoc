package ai.csap.example.model;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;
import lombok.Data;

/**
 * Common Response Wrapper
 *
 * @author CSAP Team
 */
@Data
@ApiModel(description = "通用响应结果")
public class Response<T> {

    @ApiModelProperty(value = "状态码", example = "200", forceRep = true)
    private Integer code;

    @ApiModelProperty(value = "响应消息", example = "操作成功", forceRep = true)
    private String message;

    @ApiModelProperty(value = "响应数据", forceRep = true)
    private T data;

    @ApiModelProperty(value = "时间戳", example = "1704096000000", forceRep = true)
    private Long timestamp;

    public Response() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Response<T> success(T data) {
        Response<T> response = new Response<>();
        response.setCode(200);
        response.setMessage("操作成功");
        response.setData(data);
        return response;
    }

    public static <T> Response<T> success(String message, T data) {
        Response<T> response = new Response<>();
        response.setCode(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static <T> Response<T> error(String message) {
        Response<T> response = new Response<>();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }

    public static <T> Response<T> error(Integer code, String message) {
        Response<T> response = new Response<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}

