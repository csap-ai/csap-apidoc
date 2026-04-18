package ai.csap.apidoc.strategy;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.properties.CsapApiInfo;
import ai.csap.apidoc.util.TypeVariableModel;

import cn.hutool.core.collection.CollectionUtil;

/**
 * 文档策略
 *
 * @Author ycf
 * @Date 2021/9/9 3:30 下午
 * @Version 1.0
 */
public interface ApidocStrategy extends ApidocStrategyName {
    CsapApiInfo DEFAULT_APIINFO = new CsapApiInfo("Api Documentation", "Api Documentation", "1.0", "urn:tos",
            new CsapApiInfo.Contact("", "", ""), "Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0");
    /**
     * 文件路径
     */
    String API_PATH = "csap-api-doc";
    /**
     * 文件前缀
     */
    String DEFAULT_PREFIX_NAME = "api";
    /**
     * 默认标识
     */
    String DEFAULT = "default";

    /**
     * 获取接口文档信息
     *
     * @param className       表名称
     * @param strategyModel   策略信息
     * @param isParent        是否只需要接口和方法
     * @param csapDocResponse 文档返回结果
     * @return 文档信息
     */
    CsapDocResponse apidoc(String className,
                           Boolean isParent,
                           StrategyModel strategyModel,
                           CsapDocResponse csapDocResponse);

    /**
     * 单个策略执行
     *
     * @param strategyModel 策略模型
     * @param className     实际名称
     * @return 结果
     */
    CsapDocModelController controller(StrategyModel strategyModel,
                                      String className);

    /**
     * 单个方法执行
     *
     * @param docController     方法的类
     * @param method            方法
     * @param typeVariableModel 泛型
     * @param strategyModel     策略模式
     * @return 方法结果
     */
    CsapDocMethod method(CsapDocModelController docController,
                         Method method,
                         TypeVariableModel typeVariableModel,
                         StrategyModel strategyModel);

    /**
     * 写入文档 开发模式使用
     *
     * @param standardProperties 写入的标准属性
     * @return 写入结果
     */
    Boolean write(StandardProperties standardProperties);

    /**
     * 查找是否有相同的方法
     *
     * @param docController controller
     * @param docMethod     method
     * @return true/false
     */
    default boolean containsPathMethod(CsapDocModelController docController, CsapDocMethod docMethod) {
        List<String> paths = Arrays.asList(docMethod.getPaths());
        if (CollectionUtil.isEmpty(paths)) {
            return false;
        }
        return docController.getMethodList().stream().anyMatch(i -> {
            if (Arrays.stream(i.getPaths()).anyMatch(ii -> paths.stream().anyMatch(ii::equals))) {
                return docMethod.getMethods().stream().anyMatch(x -> i.getMethods().stream().anyMatch(xx -> xx.equals(x)));
            }
            return false;
        });
    }
}
