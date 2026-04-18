package ai.csap.apidoc.enums;

import ai.csap.apidoc.annotation.EnumMessage;
import ai.csap.apidoc.annotation.EnumValue;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author ycf
 * @Date 2021/11/2 11:54 下午
 * @Version 1.0
 */
@AllArgsConstructor
@Getter
public enum YamlEnum {
    CODE_1("1", "成功"),
    CODE_2("2", "失败");
    @EnumValue
    private String code;
    @EnumMessage
    private String message;
}
