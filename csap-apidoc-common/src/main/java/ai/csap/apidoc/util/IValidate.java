package ai.csap.apidoc.util;

/**
 * @author yangchengfu
 * @description 异常接口
 * @dataTime 2019年-12月-04日 16:53:00
 **/
public interface IValidate<CODE, DATA> {
    String DEFAULT_CODE = "400";
    /**
     * code键名称
     */
    String CODE = "code";
    /**
     * 消息键名称
     */
    String MESSAGE = "message";

    String DOT = ".";
    String EMPTY = "";

    /**
     * 获取返回的编码
     *
     * @return
     */
    CODE getCode();

    /**
     * 获取返回的描述信息
     *
     * @return
     */
    String getMessage();

    /**
     * 错误附带的数据
     *
     * @return
     */
    default DATA getData() {
        return null;
    }

    /**
     * 获取名称
     *
     * @return
     */
    default String getName() {
        return "";
    }
}
