package ai.csap.apidoc;

import ai.csap.apidoc.core.ApidocStrategyName;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文件策略类型
 *
 * @Author ycf
 * @Date 2021/11/3 6:05 下午
 * @Version 1.0
 */
@AllArgsConstructor
@Getter
public enum ApiStrategyType implements ApidocStrategyName {
    //json文件
    JSON("json", ".json"),
    //注解
    ANNOTATION("annotation", ""),
    /**
     * sql lite数据库模式
     */
    SQL_LITE("sql_lite", ".db"),
    /**
     * 数据库模式:规则存在应用自己的关系数据库(MySQL / PostgreSQL / 达梦 / 人大金仓),只用于 paramType
     *
     * @since 1.4.0
     */
    DATABASE("database", ""),
    /**
     * Redis 模式:规则存在应用自己的 Redis 里,只用于 paramType
     *
     * @since 1.4.0
     */
    REDIS("redis", ""),

    //yaml文件
    YAML("yaml", ".yaml");
    /**
     * 名称
     */
    private final String name;
    /**
     * 后缀
     */
    private final String suffix;

    @Override
    public ApidocStrategyName strategyType() {
        return this;
    }
}
