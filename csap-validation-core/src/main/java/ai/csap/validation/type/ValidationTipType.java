package ai.csap.validation.type;

import ai.csap.validation.factory.Validate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 验证类型
 *
 * @Author ycf
 * @Date 2021/9/29 6:08 下午
 * @Version 1.0
 */
@AllArgsConstructor
@Getter
public enum ValidationTipType {
    //验证类型
    TIP_1("字段%s,%s", "TIP_1", "字段graphId,只能是0-9的数字", "第一个%s为字段名称，第二个%s为message", 1),
    TIP_11("字段%s%s", "TIP_11", "字段graphId只能是0-9的数字", "第一个%s为字段名称，第二个%s为message", 1),
    TIP_2("%s,%s", "TIP_2", "graphId,只能是0-9的数字", "第一个%s为字段名称，第二个%s为message", 1),
    TIP_22("%s%s", "TIP_22", "graphId只能是0-9的数字", "第一个%s为字段名称，第二个%s为message", 1),
    TIP_3("%s,%s", "TIP_3", "用户名,只能是0-9的数字", "第一个%s为字段描述名称，第二个%s为message", 2),
    TIP_33("%s%s", "TIP_33", "用户名只能是0-9的数字", "第一个%s为字段描述名称，第二个%s为message", 2),
    TIP_3_1("%s", "TIP_3_1", "只能是0-9的数字", "第二个%s为message", 3);
    /**
     * 类型值
     */
    private final String value;
    /**
     * 编码
     */
    private final String code;
    /**
     * 示例
     */
    private final String example;
    /**
     * 描述
     */
    private final String descr;
    /**
     * 1字段 2描述3直取message
     */
    private final Integer type;

    /**
     * 获取验证消息
     *
     * @param validateField 验证信息
     * @param message       验证消息
     * @return 结果
     */
    public String validateMessage(Validate.ValidateField validateField, String message) {
        if (type == 3) {
            return String.format(this.getValue(), message);
        }
        return String.format(this.getValue(), type == 1 ? validateField.getFieldName() : validateField.getRemark(), message);
    }
}
