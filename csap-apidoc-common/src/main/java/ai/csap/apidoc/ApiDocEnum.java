package ai.csap.apidoc;

/**
 * @author yangchengfu
 * @description 常量信息
 * @dataTime 2020年-03月-14日 13:51:00
 **/
public enum ApiDocEnum {
    //文档枚举
    VALUE("value", "value值"), NAME("name", "名称"),
    DESCRIPTION("description", "描述"), DATATYPECLASS("dataTypeClass", "数据类型"),
    POSITION("position", "位置排序"), HIDDEN("hidden", "是否隐藏"),
    EXAMPLE("example", "示例"), DEFAULT_VALUE("defaultValue", "默认值"),
    PARAM_TYPE("paramType", "参数类型"), REQUIRED("required", "是否必传"),
    GROUPS("groups", "分组"), REQUEST("request", "分组请求字段"),
    RESPONSE("response", "分组返回字段"), MESSAGE("message", "消息描述"),
    CODE("code", "编码"), INCLUDE("include", "是否包含");
    /**
     * 名称
     */
    private final String name;
    /**
     * 描述
     */
    private final String descr;

    ApiDocEnum(String name, String descr) {
        this.name = name;
        this.descr = descr;
    }

    public String getName() {
        return name;
    }

    public String getDescr() {
        return descr;
    }
}
