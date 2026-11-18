package ai.csap.apidoc.util;

import ai.csap.apidoc.core.ApidocResultEnum;

/**
 * 规则存储不可写.
 * <p>典型场景:以打包后的 jar({@code java -jar})运行时,YAML / JSON 策略要把规则文件写回 classpath 目录,
 * 而 classpath 在 jar 内,无法写入。DevTools 接口捕获本异常后返回 {@code code = 1007} 的失败结果和下面的说明,
 * 而不是 500。
 *
 * @author ycf
 * @since 1.3.0
 */
public class RuleStorageNotWritableException extends ValidateException {

    /**
     * 打包运行时 DevTools 能保存规则的做法(中文)
     */
    public static final String HOW_TO_SAVE_ZH = "可以:"
            + "1) 在源码目录运行(IDE 或 mvn spring-boot:run),规则写回 src/main/resources 下的规则文件;"
            + "2) 改用 SQLite 策略 @EnableApidoc(paramType = ApiStrategyType.SQL_LITE),"
            + "打包运行时规则保存在 jar 外、启动目录下的 SQLite 文件(默认 ./csap-api-doc/apidoc.db),重启后仍在;"
            + "3) 1.4.0 起可使用数据库模式 @EnableApidoc(paramType = ApiStrategyType.DATABASE),"
            + "规则存进应用自己的关系数据库(MySQL / PostgreSQL / 达梦 / 人大金仓),多实例自动同步;"
            + "也可使用 Redis 模式 @EnableApidoc(paramType = ApiStrategyType.REDIS)。";

    /**
     * 打包运行时 DevTools 能保存规则的做法(英文)
     */
    public static final String HOW_TO_SAVE_EN = "Options: "
            + "1) run from the source tree (IDE or mvn spring-boot:run) so rules are written back under src/main/resources; "
            + "2) switch to the SQLite strategy @EnableApidoc(paramType = ApiStrategyType.SQL_LITE): "
            + "when running from a jar, rules are stored in a SQLite file outside the jar under the working directory "
            + "(default ./csap-api-doc/apidoc.db) and survive restarts; "
            + "3) since 1.4.0 the database mode @EnableApidoc(paramType = ApiStrategyType.DATABASE) keeps rules in your own "
            + "relational database (MySQL / PostgreSQL / DM / KingbaseES) and syncs instances automatically; "
            + "or the Redis mode @EnableApidoc(paramType = ApiStrategyType.REDIS).";

    public RuleStorageNotWritableException(String message) {
        super(ApidocResultEnum.RULE_STORAGE_NOT_WRITABLE.getCode(), message);
    }

    public RuleStorageNotWritableException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }

    /**
     * YAML / JSON 规则文件位于 jar 内(classpath 不是目录)时的说明
     *
     * @param strategy 策略名称,如 YAML、JSON
     * @param location classpath 根的实际位置(可能为 null)
     * @return 异常
     */
    public static RuleStorageNotWritableException classpathNotWritable(String strategy, Object location) {
        String where = location == null ? "classpath 根目录不可用" : "classpath 根为 " + location;
        String whereEn = location == null ? "classpath root is not available" : "classpath root is " + location;
        return new RuleStorageNotWritableException("当前以打包后的 jar 运行(" + where + "),"
                + strategy + " 规则文件位于 jar 内,DevTools 无法保存。" + HOW_TO_SAVE_ZH
                + " | Running from a packaged jar (" + whereEn + "): the " + strategy
                + " rule files are inside the jar, so DevTools cannot save them. " + HOW_TO_SAVE_EN);
    }
}
