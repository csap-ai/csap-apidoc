package ai.csap.apidoc.annotation;



import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 实体类属性
 * @dataTime 2019年-12月-26日 17:14:00
 **/
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiModelProperty {

    /**
     * 字段描述.
     */
    String value();

    /**
     * 字段属性名
     *
     * @return
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
     */
    Class<?> dataTypeClass() default Void.class;

    /**
     * 位置
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
     */
    boolean hidden() default false;


    /**
     * 示例.
     */
    String example() default "";

    /**
     * 默认值
     *
     * @return
     */
    String defaultValue() default "";

    /**
     * 参数类型
     * <p>
     * Valid values are {@code path}, {@code query}, {@code body},
     * {@code header} or {@code form}.
     */
    ParamType paramType() default ParamType.QUERY;

    /**
     * 是否必传
     */
    boolean required() default false;

    /**
     * 请求强制显示，忽略所有group 只要有用到就会显示.
     * <p>优先级小于 ignoreReq
     *
     * @return force request display flag
     */
    boolean forceReq() default false;

    /**
     * 返回强制显示，忽略所有group 只要有用到就会显示.
     * <p>优先级小于 ignoreRep
     *
     * @return force response display flag
     */
    boolean forceRep() default false;

    /**
     * 请求强制忽略 忽略所有group 只要有用到就会忽略
     * <p>优先级大于 forceReq
     *
     * @return
     */
    boolean ignoreReq() default false;

    /**
     * 返回强制忽略，忽略所有group 只要有用到就会忽略
     * <p>优先级大于 forceRep
     *
     * @return
     */
    boolean ignoreRep() default false;


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

    /**
     * 请求 处理的参数列表
     *
     * @return
     */
    Group[] groups() default {};


}
