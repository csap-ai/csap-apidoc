package ai.csap.apidoc.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author yangchengfu
 * @description 标识一个http接口
 * @dataTime 2019年-12月-26日 17:20:00
 **/
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiOperation {
    /**
     * 接口名称
     *
     * @return
     */
    String value();


    /**
     * 描述
     *
     * @return
     */
    String description() default "";

    /**
     * 是否隐藏
     *
     * @return
     */
    boolean hidden() default false;

    /**
     * 标签
     *
     * @return
     */
    String[] tags() default {};

    /**
     * 头部文件
     * 多示例：Content-Type=application/json,Accept=text/plain
     * 单示例：Content-Type=application/json
     *
     * @return
     */
    Headers[] headers() default {@Headers(value = "application/json", key = "Content-Type", required = true, description = "上下文类型", example = "Content-Type=application/json")};

    /**
     * 参数类型
     * <p>
     * Valid values are {@code path}, {@code query}, {@code body},
     * {@code header} or {@code form}.
     * 如果 设置该参数，则表示当前方法所有属性的请求参数类型都跟着当前值走
     */
    ParamType paramType() default ParamType.DEFAULT;

    /**
     * api 状态
     *
     * @return
     */
    ApiStatus status() default ApiStatus.DEFAULT;

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
     * 是否需要认证
     *
     * @return
     */
    boolean auth() default true;

    /**
     * 认证类型
     *
     * @return
     */
    ApiAuthorization authType() default ApiAuthorization.OAUTH_2;
}
