package ai.csap.validation.type;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @Author ycf
 * @Date 2021/11/11 10:32 下午
 * @Version 1.0
 */
@AllArgsConstructor
@Getter
public enum ValidatePatternType {
    //常规表达式
    PATTERN_00("不能为空", "NotNull", 1),
    PATTERN_01("不能为空字符串", "NotEmpty", 1),
    //校验数字的表达式
    PATTERN_1("数字", "^[0-9]*$", 2),
    PATTERN_2("n位的数字", "^\\d{13}$", 2),
    PATTERN_3("至少n位的数字", "^\\d{13,}$", 2),
    PATTERN_4("m-n位的数字", "^\\d{10,20}$", 2),
    PATTERN_5("零和非零开头的数字", "^(0|[1-9][0-9]*)$", 2),
    PATTERN_6("非零开头的最多带两位小数的数字", "^([1-9][0-9]*)+(\\.[0-9]{1,2})?$", 2),
    PATTERN_7("带1-2位小数的正数或负数", "^(\\-)?\\d+(\\.\\d{1,2})$", 2),
    PATTERN_8("正数、负数、和小数", "^(\\-|\\+)?\\d+(\\.\\d+)?$", 2),
    PATTERN_9("有两位小数的正实数", "^[0-9]+(\\.[0-9]{2})?$", 2),
    PATTERN_10("有1~3位小数的正实数", "^[0-9]+(\\.[0-9]{1,3})?$", 2),
    PATTERN_11("非零的正整数", "^[1-9]\\d*$", 2),
    PATTERN_12("非零的负整数", "^\\-[1-9][]0-9\"*$", 2),
    PATTERN_13("非负整数", "^\\d+$", 2),
    PATTERN_14("非正整数", "^-[1-9]\\d*|0$", 2),
    PATTERN_15("非负浮点数", "^\\d+(\\.\\d+)?$", 2),
    PATTERN_16("非正浮点数", "^((-\\d+(\\.\\d+)?)|(0+(\\.0+)?))$", 2),
    PATTERN_17("正浮点数", "^[1-9]\\d*\\.\\d*|0\\.\\d*[1-9]\\d*$", 2),
    PATTERN_18("负浮点数", "^-([1-9]\\d*\\.\\d*|0\\.\\d*[1-9]\\d*)$", 2),
    PATTERN_19("浮点数", "^(-?\\d+)(\\.\\d+)?$", 2),
    //校验字符的表达式
    PATTERN_20("汉字", "^[\\u4e00-\\u9fa5]{0,}$", 3),
    PATTERN_21("英文和数字", "^[A-Za-z0-9]+$ 或 ^[A-Za-z0-9]{4,40}$", 3),
    PATTERN_22("长度为3-20的所有字符", "^.{3,20}$", 3),
    PATTERN_23("由26个英文字母组成的字符串", "^[A-Za-z]+$", 3),
    PATTERN_24("由26个大写英文字母组成的字符串", "^[A-Z]+$", 3),
    PATTERN_25("由26个小写英文字母组成的字符串", "^[a-z]+$", 3),
    PATTERN_26("由数字和26个英文字母组成的字符串", "^[A-Za-z0-9]+$", 3),
    PATTERN_27("由数字、26个英文字母或者下划线组成的字符串", "^\\w+$ ", 3),
    PATTERN_28("中文、英文、数字包括下划线", "^[\\u4E00-\\u9FA5A-Za-z0-9_]+$", 3),
    PATTERN_29("由中文、英文、数字但不包括下划线等符号", "^[\\u4E00-\\u9FA5A-Za-z0-9]+$", 3),
    PATTERN_30("可以输入含有^%&',;=?$\\\"等字符", "[^%&',;=?$\\x22]+ ", 3),
    PATTERN_31("禁止输入含有~的字符", "[^~\\x22]+", 3),
    //特殊需求表达式
    PATTERN_32("Email地址", "^\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*$", 4),
    PATTERN_33("域名", "[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\\.? ", 4),
    PATTERN_34("InternetURL", "^http://([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?$", 4),
    PATTERN_35("手机号码", "^(13[0-9]|14[5|7]|15[0|1|2|3|4|5|6|7|8|9]|18[0|1|2|3|5|6|7|8|9])\\d{8}$", 4),
    PATTERN_36("电话号码", "^(\\(\\d{3,4}-)|\\d{3.4}-)?\\d{7,8}$", 4),
    PATTERN_37("国内电话号码", "\\d{3}-\\d{8}|\\d{4}-\\d{7}", 4),
    PATTERN_38("身份证号(15位、18位数字)，最后一位是校验位，可能为数字或字符X", "(^\\d{15}$)|(^\\d{18}$)|(^\\d{17}(\\d|X|x)$)", 4),
    PATTERN_39("日期格式", "^\\d{4}-\\d{1,2}-\\d{1,2}", 4),
    PATTERN_40("一年的12个月(01～09和1～12)", "^(0?[1-9]|1[0-2])$", 4),
    PATTERN_41("一个月的31天(01～09和1～31)", "^((0?[1-9])|((1|2)[0-9])|30|31)$\n", 4),
    PATTERN_42("xml文件", "^([a-zA-Z]+-?)+[a-zA-Z0-9]+\\\\.[x|X][m|M][l|L]$", 4),
    PATTERN_43("中国邮政编码", "[1-9]\\d{5}(?!\\d)", 4),
    PATTERN_44("IPv4地址", "((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})(\\.((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})){3}", 3);
    /**
     * 描述
     */
    private final String descr;
    /**
     * 正则表达式
     */
    private final String pattern;
    /**
     * 1 校验数字的表达式
     * 2 校验字符的表达式
     * 3 特殊需求表达式
     */
    private final Integer type;

    public static List<ValidatePatternTypeModel> getAll() {
        return Stream.of(values())
                .map(ValidatePatternTypeModel::set)
                .collect(Collectors.toList());
    }

    @Data
    @Builder
    public static class ValidatePatternTypeModel {
        /**
         * 正则表达式
         */
        private String pattern;
        /**
         * 描述
         */
        private String descr;
        /**
         * 1 校验数字的表达式
         * 2 校验字符的表达式
         * 3 特殊需求表达式
         * 4 固定类型字符串判断
         */
        private Integer type;
        /**
         * 等级
         */
        private Integer level;
        /**
         * 编号
         */
        private String code;
        /**
         * 消息
         */
        private String message;

        public static ValidatePatternTypeModel set(ValidatePatternType v) {
            return ValidatePatternTypeModel.builder()
                    .pattern(v.getPattern())
                    .type(v.getType())
                    .level(1)
                    .code("1001")
                    .message("数据不能为空")
                    .descr(v.getDescr())
                    .build();
        }
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class PatternMap {
        private List<ValidatePatternTypeModel> patternList;
        private List<ValidatePatternTypeModel> patternTypeList;
        public static final List<ValidatePatternTypeModel> PATTERN_DESCR = Lists.newArrayList();

        static {
            PATTERN_DESCR.add(ValidatePatternTypeModel.builder().type(1).descr("常规表达式").build());
            PATTERN_DESCR.add(ValidatePatternTypeModel.builder().type(2).descr("校验数字的表达式").build());
            PATTERN_DESCR.add(ValidatePatternTypeModel.builder().type(3).descr("校验字符的表达式").build());
            PATTERN_DESCR.add(ValidatePatternTypeModel.builder().type(4).descr("特殊需求表达式").build());
        }
    }


}
