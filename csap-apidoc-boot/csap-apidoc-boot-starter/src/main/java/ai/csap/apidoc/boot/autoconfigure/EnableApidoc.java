package ai.csap.apidoc.boot.autoconfigure;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import ai.csap.apidoc.ApiParamStrategyType;
import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.strategy.ApidocStrategy;

/**
 * 启用多配置文档注解.
 * <p>Created on 2020/9/22
 *
 * @author yangchengfu
 * @since 1.0
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Import(ApidocImportBeanDefinitionRegistrar.class)
public @interface EnableApidoc {

    @AliasFor("apiPackages")
    String[] value() default {};

    /**
     * 接口文档策略类型 默认为注解
     */
    ApiStrategyType type() default ApiStrategyType.ANNOTATION;

    /**
     * 文档参数分组类型 默认为注解(实际作用是标识每个接口请求参数和返回参数的处理策略)
     */
    ApiStrategyType paramType() default ApiStrategyType.ANNOTATION;

    /**
     * 接口请求的参数策略类型 {@link ai.csap.apidoc.ApiParamStrategyType}
     */
    String requestType() default ApiParamStrategyType.DEFAULT_TYPE;

    /**
     * 接口返回参数策略类型 {@link ai.csap.apidoc.ApiParamStrategyType}
     */
    String responseType() default ApiParamStrategyType.DEFAULT_TYPE;

    /**
     * 文件路径 多服务区分使用
     */
    String path() default ApidocStrategy.API_PATH;

    /**
     * 文件名称(目前只有文档模式为sqlite时生效)如果不填写,取默认值 {@link ai.csap.apidoc.SQLiteHandle}
     *
     * @return 文件名称
     */
    String fileName() default "";

    /**
     * 文档文件前缀
     */
    FilePrefixStrategyType prefixStrategy() default FilePrefixStrategyType.DEFAULT;

    /**
     * 指定类
     */
    Class<?>[] apiPackageClasses() default {};

    /**
     * API包扫描
     */
    @AliasFor("value")
    String[] apiPackages() default {};

    /**
     * 接口扫描包.
     */
    String[] enumPackages() default {};

    /**
     * 指定类.
     */
    Class<?>[] enumPackageClasses() default {};

    /**
     * 接口层扫描包
     */
    String[] modelPackages() default {};

    /**
     * 指定类
     */
    Class<?>[] modelPackageClasses() default {};

    /**
     * 是否扫描子包
     */
    boolean showChildPackageFlag() default true;
}
