package ai.csap.apidoc.core;

import ai.csap.apidoc.annotation.EnumMessage;
import ai.csap.apidoc.annotation.EnumValue;

import lombok.Getter;

/**
 * @author ycf
 */

@Getter
public enum ApidocResultEnum {
    SYSTEM_ERROR("9999", "系統异常,请稍后再试..."),
    SERVICE_ERROR("1006", "服务器异常,请稍后再试..."),
    SUCCESS("0", "成功"),
    DEFAULT("400", "默认"),
    NOT_LOGIN("9998", "用户未登录"),
    TOKEN_IS_NOT_AVAILABLE("9997", "用户token失效"),
    DATA_ERROR("9996", "数据异常,请核对"),
    NOT_ROEL("9990", "没有权限"),
    result_1("1002", "添加失败"),
    result_2("1003", "删除失败"),
    result_3("1004", "修改失败"),
    DATA_VALIDATE_ERROT("400", "数据格式错误");
    /**
     * 结果码
     */
    @EnumValue
    private final String code;

    /**
     * 结果描述
     */
    @EnumMessage
    private final String desc;

    /**
     * 获取结果码
     *
     * @param code 待查询code
     * @return 对应的结果码
     */
    public static ApidocResultEnum getByCode(String code) {
        for (ApidocResultEnum resultCode : values()) {
            if (resultCode.getCode().equals(code)) {
                return resultCode;
            }
        }
        return null;
    }

    /**
     * @param code
     * @param desc
     */
    ApidocResultEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return getCode();
    }
}
