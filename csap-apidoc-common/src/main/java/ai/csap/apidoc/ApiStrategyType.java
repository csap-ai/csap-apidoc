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
