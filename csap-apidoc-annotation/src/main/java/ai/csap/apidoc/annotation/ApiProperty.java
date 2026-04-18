package ai.csap.apidoc.annotation;



import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description api 的属性。为了单个参数使用
 * 支持方法级和参数级两种使用方式：
 * 1. 方法级：标注在方法上，通过 name 属性匹配参数名（向后兼容）
 * 2. 参数级：直接标注在参数上，更直观（推荐）
 * @dataTime 2020年-02月-18日 18:23:00
 **/
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ApiProperty {

    /**
     * 字段描述.
     */
    String value();

    /**
     * 字段属性名
     * 方法级使用时必填，用于匹配参数名
     * 参数级使用时可选，自动使用参数名
     *
     * @return 参数名称
     */
    String name() default "";

    /**
     * 字段详细描述
     *
     * @return
     */
    String description() default "";

    /**
     * 数据类型
     *
     * @return class
     */
    Class dataTypeClass() default Void.class;

    /**
     * 位置
     *
     * @return int
     */
    int position() default 0;
    /**
     * 长度
     */
    int length() default 0;

    /**
     * 小数
     */
    int decimals() default 0;

    /**
     * 是否隐藏
     *
     * @return boolean
     */
    boolean hidden() default false;


    /**
     * 示例.
     *
     * @return String
     */
    String example() default "";

    /**
     * 默认值
     *
     * @return String
     */
    String defaultValue() default "";

    /**
     * 是否必传
     *
     * @return
     */
    boolean required() default false;


    /**
     * 参数类型
     * <p>
     * Valid values are {@code path}, {@code query}, {@code body},
     * {@code header} or {@code form}.
     */
    ParamType paramType() default ParamType.QUERY;

    /**
     * 分组
     *
     * @return
     */
    String[] group() default "default";

    /**
     * 版本
     *
     * @return
     */
    String[] version() default "default";
}
