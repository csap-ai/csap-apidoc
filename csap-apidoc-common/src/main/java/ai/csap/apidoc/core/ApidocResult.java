package ai.csap.apidoc.core;

import static ai.csap.apidoc.core.ApidocResultEnum.SUCCESS;

import java.io.Serializable;

import ai.csap.apidoc.annotation.ApiModel;
import ai.csap.apidoc.annotation.ApiModelProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


/**
 * 统一API响应结果封装类
 * 用于标准化所有API接口的返回格式，确保前后端交互的一致性
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>封装业务处理结果，包括成功和失败情况</li>
 *   <li>提供统一的响应码、消息和数据结构</li>
 *   <li>支持国际化语言设置</li>
 *   <li>记录服务端响应时间戳</li>
 * </ul>
 *
 * @param <M> 响应数据的泛型类型
 * @author ycf
 */
@Data
@Builder
@ApiModel(value = "公用参数", description = "公用参数说明")
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class ApidocResult<M> implements Serializable {
    /**
     * 响应状态码
     * 0表示成功，其他值表示不同类型的错误
     */
    @ApiModelProperty(value = "返回编码", example = "0", forceRep = true)
    private String code;

    /**
     * 响应消息描述
     * 用于向客户端说明操作结果
     */
    @ApiModelProperty(value = "返回的描述", example = "成功", forceRep = true)
    private String message;

    /**
     * 响应数据载体
     * 携带具体的业务数据，类型由泛型参数M决定
     */
    @ApiModelProperty(value = "返回的数据", forceRep = true)
    private M data;

    /**
     * 语言标识
     * 用于国际化场景，默认为中文(ZH)
     */
    @ApiModelProperty(value = "语言", forceRep = true)
    private String language = "ZH";

    /**
     * 服务端响应时间戳（毫秒）
     * 用于记录响应生成时刻，便于性能分析和问题排查
     */
    @ApiModelProperty(value = "服务端实时时间", example = "1540483321460", forceRep = true)
    private Long time = System.currentTimeMillis();

    /**
     * 获取语言标识，确保不返回null
     *
     * @return 语言标识，默认为"ZH"
     */
    public String getLanguage() {
        return language == null ? "ZH" : language;
    }

    /**
     * 判断当前响应是否为成功状态
     *
     * @return true表示成功，false表示失败
     */
    public boolean isSuccess() {
        return SUCCESS.getCode().equals(code);
    }

    /**
     * 判断当前响应是否为失败状态
     *
     * @return true表示失败，false表示成功
     */
    public boolean noSuccess() {
        return !isSuccess();
    }

    /**
     * 构建成功响应（带数据和自定义消息）
     *
     * @param object  响应数据对象
     * @param message 自定义成功消息
     * @param <T>     数据类型
     * @return 成功的响应对象
     */
    public static <T> ApidocResult<T> success(T object, String message) {
        return new ApidocResult<T>().setCode(SUCCESS.getCode()).setMessage(message).setData(object);
    }

    /**
     * 构建成功响应（无数据，默认消息）
     * 适用于只需要通知成功，不需要返回数据的场景
     *
     * @return 成功的响应对象
     */
    public static ApidocResult<Object> success() {
        return success(null, SUCCESS.getDesc());
    }

    /**
     * 构建成功响应（带数据，默认消息）
     *
     * @param object 响应数据对象
     * @param <T>    数据类型
     * @return 成功的响应对象
     */
    public static <T> ApidocResult<T> success(T object) {
        return success(object, SUCCESS.getDesc());
    }

    /**
     * 构建错误响应（带错误码、消息和数据）
     *
     * @param code 错误码
     * @param msg  错误消息
     * @param data 附加数据（可选）
     * @param <T>  数据类型
     * @return 错误的响应对象
     */
    public static <T> ApidocResult<T> error(String code, String msg, T data) {
        return new ApidocResult<T>().setCode(code).setMessage(msg).setData(data);
    }

    /**
     * 构建错误响应（带错误码和消息）
     *
     * @param code 错误码
     * @param msg  错误消息
     * @param <T>  数据类型
     * @return 错误的响应对象
     */
    public static <T> ApidocResult<T> error(String code, String msg) {
        return new ApidocResult<T>().setCode(code).setMessage(msg);
    }

    /**
     * 构建服务异常响应
     * 用于表示服务内部错误的标准响应
     *
     * @param <T> 数据类型
     * @return 服务异常的响应对象
     */
    public static <T> ApidocResult<T> serviceError() {
        return new ApidocResult<T>().setCode(ApidocResultEnum.SERVICE_ERROR.getCode()).setMessage(ApidocResultEnum.SERVICE_ERROR.getDesc());
    }
}
