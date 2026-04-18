package ai.csap.apidoc.autoconfigure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.FilePrefixStrategyType;

import lombok.Data;

/**
 * @author yangchengfu
 * @description 自动化配置
 * @dataTime 2020年-09月-22日 15:28:00
 **/
@Data
public class EnableApidocConfig {
    /**
     * yaml、json,db等 形式的扫描路径
     */
    private String path;
    /**
     * 文件名称 目前只有db模式生效
     */
    private String fileName;
    /**
     * 文档策略类型
     */
    private ApiStrategyType type;
    /**
     * 方法参数类型
     */
    private ApiStrategyType paramType;
    /**
     * 请求的参数类型
     */
    private String requestType;
    /**
     * 返回的参数类型
     */
    private String responseType;
    /**
     * 文件前缀策略类型
     */
    private FilePrefixStrategyType prefixStrategy;
    /**
     * 扫描的包
     */
    private List<StrategyModel> apiPackageClass = new ArrayList<>(16);

    private Set<Class<?>> findlClazz = new HashSet<>();

    /**
     * 返回的枚举类，扫描包
     */
    private List<String> enumPackages = new ArrayList<>(16);

    /**
     * 模型/实体类扫描包路径
     */
    private List<String> modelPackages = new ArrayList<>(16);


    /**
     * 返回的枚举类，扫描包
     */
    private List<Class<?>> enumPackageClass = new ArrayList<>(16);

    /**
     * 模型/实体类扫描包路径
     */
    private List<Class<?>> modelPackageClass = new ArrayList<>(16);

    private boolean contains(StrategyModel strategyModel) {
        return findlClazz.containsAll(strategyModel.getClazz());
    }

    public void addApiPackageClass(StrategyModel strategyModel) {
        if (contains(strategyModel)) {
            return;
        }
        strategyModel.setId(UUID.randomUUID().toString());
        findlClazz.addAll(strategyModel.getClazz());
        apiPackageClass.add(strategyModel);
    }

    public Optional<StrategyModel> filterStrategy(Class<?> aClass) {
        return this.apiPackageClass.stream().filter(i -> i.getClazz().contains(aClass)).findFirst();
    }
}
