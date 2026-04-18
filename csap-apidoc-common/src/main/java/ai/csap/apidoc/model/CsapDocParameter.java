package ai.csap.apidoc.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.type.ModelType;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API文档参数信息模型
 * 表示接口方法中的单个参数的详细信息
 *
 * <p>主要用途：</p>
 * <ul>
 *   <li>描述请求参数的结构和约束</li>
 *   <li>描述响应数据的字段信息</li>
 *   <li>支持嵌套对象的递归表示</li>
 *   <li>提供参数验证规则</li>
 * </ul>
 *
 * @author yangchengfu
 * @dataTime 2019年-12月-27日 17:00:00
 **/
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CsapDocParameter implements Cloneable {
    /**
     * 参数的简短描述
     * 通常来自@ApiModelProperty注解的value属性
     * 用于在文档中快速说明参数用途
     */
    private String value;

    /**
     * 参数的字段名称
     * 对应Java类中的字段名或方法参数名
     */
    private String name;

    /**
     * 参数的详细描述
     * 提供比value更详细的说明信息
     * 当为空时，getDescription()方法会返回value的值
     */
    private String description;

    /**
     * 参数的示例值
     * 用于在文档中展示参数的典型取值
     * 帮助使用者理解参数格式
     */
    private String example;

    /**
     * 参数的默认值
     * 当请求中未提供此参数时使用的默认值
     */
    private String defaultValue;

    /**
     * 参数是否必填
     * true表示必须提供，false表示可选
     */
    private Boolean required;

    /**
     * 参数的数据类型（简写）
     * 如"String"、"Integer"、{@code "List<User>"}等
     * 用于文档界面的简洁展示
     */
    private String dataType;

    /**
     * 参数的完整数据类型（全限定名）
     * 如"java.lang.String"、"com.example.User"等
     * 用于精确标识类型
     */
    private String longDataType;

    /**
     * 参数的传递类型
     * 如QUERY（查询参数）、PATH（路径参数）、BODY（请求体）、HEADER（请求头）等
     */
    private ParamType paramType;

    /**
     * 参数在列表中的显示位置
     * 用于控制文档中参数的排列顺序
     */
    private int position;

    /**
     * 字符串类型参数的最大长度
     * 用于数据验证和文档说明
     */
    private int length;

    /**
     * 数值类型参数的小数位数
     * 用于描述数值精度要求
     */
    private int decimals;

    /**
     * 参数所属的分组集合
     * 用于控制参数在不同场景下的可见性
     * 如"create"、"update"分组可能包含不同的字段
     */
    private Set<String> group;

    /**
     * 参数所属的版本集合
     * 用于版本控制，不同版本的接口可能有不同的参数
     */
    private Set<String> version;

    /**
     * 嵌套的子对象模型
     * 当参数类型为复杂对象时，通过此字段描述对象的内部结构
     * 支持无限层级的嵌套
     */
    private CsapDocModel children;

    /**
     * 参数的模型类型
     * 如BASE_DATA（基础数据类型）、OBJECT（对象）、ARRAY（数组）等
     * 用于区分参数的结构类型
     */
    private ModelType modelType;

    /**
     * 标识参数是否包含子字段
     * true表示此参数为复杂对象，有子字段
     */
    private Boolean hasChildren = Boolean.FALSE;

    /**
     * 是否强制显示此参数
     * true表示无论分组如何，此参数都要显示
     * 用于标记核心必要参数
     */
    private Boolean force = Boolean.FALSE;

    /**
     * 参数的唯一标识符
     * 用于在文档树结构中唯一标识一个参数节点
     * 通常使用UUID生成
     */
    private String key;

    /**
     * 扩展描述信息列表
     * 用于提供参数的额外说明，如特殊规则、注意事项等
     * 每个Map代表一条扩展信息
     */
    private List<Map<String, String>> extendDescr;

    /**
     * 是否在文档中隐藏此参数
     * true表示不在公开文档中显示
     */
    private Boolean hidden;

    /**
     * 参数的键名称
     * 用于特殊场景下的参数标识
     */
    private String keyName;

    /**
     * 参数的验证规则列表
     * 包含各种验证约束，如NotNull、Pattern、Size等
     * 用于自动生成验证文档和提示信息
     */
    private List<ValidateAttribute> validate;

    /**
     * 克隆当前参数对象
     * 实现浅拷贝，用于参数对象的复制
     *
     * @return 克隆后的参数对象
     * @throws CloneNotSupportedException 克隆不支持异常
     */
    @Override
    protected CsapDocParameter clone() throws CloneNotSupportedException {
        return (CsapDocParameter) super.clone();
    }

    /**
     * 获取参数描述
     * 优先返回详细描述，如果详细描述为空则返回简短描述
     *
     * @return 参数描述文本
     */
    public String getDescription() {
        return StrUtil.isEmpty(description) ? value : description;
    }

    /**
     * 获取嵌套子对象的所有参数列表
     * 用于递归展示复杂对象的内部结构
     *
     * @return 子参数列表，如果没有子对象则返回null
     */
    public List<CsapDocParameter> getParameters() {
        return children != null ? children.getParameters() : null;
    }

    /**
     * 验证属性内部类
     * 描述参数的验证规则和约束条件
     *
     * <p>支持的验证类型包括：</p>
     * <ul>
     *   <li>NotNull、NotEmpty - 非空验证</li>
     *   <li>Pattern - 正则表达式验证</li>
     *   <li>Size、Length - 长度验证</li>
     *   <li>Min、Max - 数值范围验证</li>
     *   <li>Email、Phone - 格式验证</li>
     * </ul>
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ValidateAttribute {
        /**
         * 验证规则的唯一编码
         * 用于标识验证规则的类型
         */
        private String code;

        /**
         * 验证失败时的提示消息
         * 向用户说明验证失败的原因
         */
        private String message;

        /**
         * 验证的正则表达式模式
         * 用于Pattern类型的验证规则
         */
        private String pattern;

        /**
         * 验证规则的详细描述
         * 说明此验证规则的用途和要求
         */
        private String descr;

        /**
         * 验证的优先级
         * 数值越小优先级越高，默认为1
         * 用于控制多个验证规则的执行顺序
         */
        private Integer level = 1;

        /**
         * 验证的类型名称
         * 如"NotNull"、"Pattern"、"Size"等
         * 对应jakarta.validation包中的注解名称
         */
        private String type;
    }
}
