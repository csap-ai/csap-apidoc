package ai.csap.apidoc.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactory;

import com.google.common.collect.Lists;

import ai.csap.apidoc.CsapHeadersProvider;
import ai.csap.apidoc.CsapResourcesProvider;
import ai.csap.apidoc.DevtoolsClass;
import ai.csap.apidoc.GlobalCsapRequestParamProvider;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.validation.factory.IValidateFactory;

import lombok.Data;

/**
 * @author yangchengfu
 * @dataTime 2021年-05月-21日 13:57:00
 **/
@Data
public abstract class AbstractApiDocService {
    /**
     * 开发工具模式下的自定义class
     */
    private DevtoolsClass devtoolsClass;
    /**
     * 配置
     */
    private ScannerPackageConfig packageConfig;
    /**
     * 多资源
     */
    private CsapResourcesProvider resourcesProvider;
    /**
     * 头部信息
     */
    private CsapHeadersProvider csapHeadersProvider;
    /**
     * 文档验证
     */
    private IValidateFactory validateFactory;
    /**
     * 全局的请求参数
     */
    protected GlobalCsapRequestParamProvider globalCsapRequestParamProvider;
    /**
     * bean工厂
     */
    protected BeanFactory beanFactory;
    /**
     * 文档配置
     */
    private CsapDocConfig csapDocConfig;
    /**
     * 全局的api
     */
    protected List<CsapDocModelController> globalApiList = Lists.newArrayList();

    /**
     * <p>
     * key：匹配方法
     * value 参数
     * <p/>
     * 全局请求参数
     */
    protected Map<String, List<CsapDocModel>> globalRequestParam = new HashMap<>();

    public AbstractApiDocService(DevtoolsClass devtoolsClass, ScannerPackageConfig packageConfig,
                                  CsapResourcesProvider resourcesProvider,
                                  CsapHeadersProvider csapHeadersProvider,
                                  IValidateFactory validateFactory,
                                  GlobalCsapRequestParamProvider globalCsapRequestParamProvider,
                                  BeanFactory beanFactory, CsapDocConfig csapDocConfig) {
        this.devtoolsClass = devtoolsClass;
        this.packageConfig = packageConfig;
        this.resourcesProvider = resourcesProvider;
        this.csapHeadersProvider = csapHeadersProvider;
        this.validateFactory = validateFactory;
        this.globalCsapRequestParamProvider = globalCsapRequestParamProvider;
        this.beanFactory = beanFactory;
        this.csapDocConfig = csapDocConfig;
    }

    public DevtoolsClass getDevtoolsClass() {
        return beanFactory.getBeanProvider(DevtoolsClass.class).getIfAvailable();
    }


    public IValidateFactory getValidateFactory() {
        if (validateFactory == null) {
            validateFactory = beanFactory.getBeanProvider(IValidateFactory.class).getIfAvailable();
        }
        return validateFactory;
    }

    public Map<String, List<CsapDocModel>> getGlobalRequestParam() {
        return globalCsapRequestParamProvider != null ? globalCsapRequestParamProvider.get() : globalRequestParam;
    }

    public List<CsapDocModel> getGlobalRequestParam(String methodName) {
        return getGlobalRequestParam().entrySet().stream().filter(i -> methodName.contains(i.getKey())).flatMap(i -> i.getValue().stream()).collect(Collectors.toList());
    }

}
