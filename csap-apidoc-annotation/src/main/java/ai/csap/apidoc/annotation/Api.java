package ai.csap.apidoc.annotation;



import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller类级别的API文档注解
 * 标注在Controller类上，用于定义整个Controller的文档信息
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>定义Controller的名称和描述</li>
 *   <li>控制接口方法的显示和隐藏</li>
 *   <li>设置分组、版本、标签等元数据</li>
 *   <li>配置协议类型和API状态</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>
 * &#64;Api(value = "用户管理", description = "用户相关的增删改查接口",
 *      group = {"admin", "user"}, version = "v1.0",
 *      tags = {"用户", "认证"})
 * &#64;RestController
 * &#64;RequestMapping("/user")
 * public class UserController {
 *     // ...
 * }
 * </pre>
 *
 * @author yangchengfu
 * @dataTime 2019年-12月-26日 16:49:00
 **/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Api {

    /**
     * Controller的名称或标题
     * 用于在文档中展示Controller的主要功能
     *
     * @return Controller名称，默认为空
     */
    String value() default "";

    /**
     * Controller的详细描述
     * 提供更详细的功能说明和使用注意事项
     *
     * @return 详细描述文本，默认为空
     */
    String description() default "";

    /**
     * Controller在文档中的排序位置
     * 数值越小越靠前，用于控制文档中Controller的显示顺序
     *
     * @return 排序位置，默认为0
     */
    int position() default 0;

    /**
     * 是否在文档中隐藏此Controller
     * true表示不在公开文档中显示，通常用于内部接口或开发中的接口
     *
     * @return true为隐藏，false为显示，默认为false
     */
    boolean hidden() default false;

    /**
     * 需要隐藏的方法名列表
     * 指定Controller中不希望在文档中展示的方法名
     *
     * <p>示例：hiddenMethod = {"internalMethod", "testMethod"}</p>
     *
     * @return 隐藏的方法名数组，默认为空
     */
    String[] hiddenMethod() default {};

    /**
     * 需要展示的方法名列表（白名单模式）
     * 如果设置了此属性，则只展示列表中的方法，其他方法都会被隐藏
     * 优先级高于hiddenMethod
     *
     * <p>示例：showMethod = {"getUser", "createUser"}</p>
     *
     * @return 展示的方法名数组，默认为空（展示所有方法）
     */
    String[] showMethod() default {};

    /**
     * Controller的标签列表
     * 用于对Controller进行分类和标记，便于文档的组织和检索
     *
     * <p>示例：tags = {"用户", "管理", "V1.0"}</p>
     *
     * @return 标签数组，默认为空
     */
    String[] tags() default {};

    /**
     * Controller所属的分组
     * 用于多模块或多租户场景下的接口分组管理
     * 一个Controller可以属于多个分组
     *
     * <p>示例：group = {"admin", "mobile", "web"}</p>
     *
     * @return 分组名称数组，默认为"default"
     */
    String[] group() default "default";

    /**
     * Controller的API版本标识
     * 用于版本控制，支持同一接口的多版本并存
     *
     * <p>示例：version = {"v1.0", "v2.0"}</p>
     *
     * @return 版本标识数组，默认为"default"
     */
    String[] version() default "default";

    /**
     * 接口支持的协议类型
     * 可选值：HTTP、HTTPS、WS（WebSocket）、WSS（WebSocket Secure）
     *
     * <p>用于标识接口的通信协议</p>
     *
     * @return 协议类型枚举，默认为HTTP
     */
    Protocols protocols() default Protocols.HTTP;

    /**
     * API的状态标识
     * 用于标识接口的生命周期阶段，如开发中、测试中、稳定、废弃等
     *
     * <p>可选值：DEFAULT、ALPHA、BETA、DEPRECATED等</p>
     *
     * @return API状态枚举，默认为DEFAULT
     */
    ApiStatus status() default ApiStatus.DEFAULT;
}
