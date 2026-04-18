package ai.csap.apidoc.autoconfigure;

import java.util.Set;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.FilePrefixStrategyType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 策略对象
 *
 * @Author ycf
 * @Date 2023/3/7 20:53
 * @Version 1.0
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StrategyModel {
    /**
     * 类信息数组
     */
    private Set<Class<?>> clazz;
    /**
     * 单个类信息
     */
    private Class<?> clz;
    /**
     * 文档类型
     */
    private ApiStrategyType docType;
    /**
     * 参数分组类型
     */
    private ApiStrategyType paramType;
    /**
     * 请求参数类型 {@link ai.csap.apidoc.ApiParamStrategyType}
     */
    private String requestType;
    /**
     * 返回参数类型 {@link ai.csap.apidoc.ApiParamStrategyType}
     */
    private String responseType;
    /**
     * 文件前缀策略类型
     */
    private FilePrefixStrategyType prefixStrategy;
    /**
     * 方法键
     */
    private String key;
    /**
     * 文件路径
     */
    private String path;
    /**
     * 开发模式
     */
    private Boolean devtools;
    /**
     * 是否只查询接口
     */
    private Boolean isParent;
    /**
     * 是否刷新
     */
    private Boolean flush;
    /**
     * 初始化的标识
     */
    private String id;
    /**
     * 文件名称
     */
    private String fileName;
}
