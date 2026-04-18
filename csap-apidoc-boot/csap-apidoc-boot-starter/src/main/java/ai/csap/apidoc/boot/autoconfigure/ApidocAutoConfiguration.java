package ai.csap.apidoc.boot.autoconfigure;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.csap.apidoc.AnnotationParamGroupStrategy;
import ai.csap.apidoc.BaseCsapResourcesProvider;
import ai.csap.apidoc.CsapHeadersProvider;
import ai.csap.apidoc.CsapResourcesProvider;
import ai.csap.apidoc.DefaultCsapHeardersProvider;
import ai.csap.apidoc.DefaultDevtoolsClass;
import ai.csap.apidoc.DefaultGlobalCsapRequestParamProvider;
import ai.csap.apidoc.DefaultGlobalCsapResponseParamProvider;
import ai.csap.apidoc.DevtoolsClass;
import ai.csap.apidoc.GlobalCsapRequestParamProvider;
import ai.csap.apidoc.GlobalCsapResponseParamProvider;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.RunApplicationListener;
import ai.csap.apidoc.config.ScannerPackageConfig;
import ai.csap.apidoc.properties.CsapDocConfig;
import ai.csap.apidoc.service.ApiDocService;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.service.method.DefaultMethodRequest;
import ai.csap.apidoc.service.method.DefaultMethodResponse;
import ai.csap.apidoc.service.standard.PostmanConverterService;
import ai.csap.apidoc.strategy.ApidocStrategy;
import ai.csap.apidoc.strategy.SQLiteParamGroupStrategy;
import ai.csap.apidoc.strategy.YamlParamGroupStrategy;
import ai.csap.apidoc.strategy.handle.ControllerHandle;
import ai.csap.apidoc.strategy.handle.EnumHandle;
import ai.csap.apidoc.strategy.handle.InfoHandle;
import ai.csap.apidoc.strategy.handle.MethodFieldHandle;
import ai.csap.apidoc.strategy.handle.MethodHandle;
import ai.csap.apidoc.strategy.handle.ParamHandle;
import ai.csap.apidoc.web.CsapDocController;
import ai.csap.validation.factory.IValidateFactory;

/**
 * Apidoc config.
 * <p>Created on 2020/1/3
 *
 * @author yangchengfu
 * @since 1.0
 */
@Configuration
@EnableConfigurationProperties({CsapDocConfig.class})
@ImportAutoConfiguration(ApidocDevtoolsAutoConfiguration.class)
@ConditionalOnBean(EnableApidocConfig.class)
public class ApidocAutoConfiguration {
    @Bean
    public ApiDocService apiDocService(BeanFactory beanFactory,
                                       ObjectProvider<IValidateFactory> validateFactories,
                                       DevtoolsClass devtoolsClass,
                                       ScannerPackageConfig packageConfig,
                                       CsapResourcesProvider resourcesProvider,
                                       CsapHeadersProvider csapHeadersProvider,
                                       GlobalCsapRequestParamProvider globalCsapRequestParamProvider,
                                       CsapDocConfig csapDocConfig) {
        return new ApiDocService(devtoolsClass, packageConfig, resourcesProvider,
                csapHeadersProvider, validateFactories.getIfAvailable(),
                globalCsapRequestParamProvider, beanFactory, csapDocConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalCsapRequestParamProvider globalCsapRequestParamProvider() {
        return new DefaultGlobalCsapRequestParamProvider();
    }

    @Bean
    public DefaultMethodRequest defaultMethodRequest(
            ObjectProvider<IValidateFactory> validateFactories,
            ScannerPackageConfig packageConfig) {
        return new DefaultMethodRequest(validateFactories.getIfAvailable(), packageConfig);
    }

    @Bean
    public DefaultMethodResponse defaultMethodResponse(
            ObjectProvider<IValidateFactory> validateFactories,
            ScannerPackageConfig packageConfig) {
        return new DefaultMethodResponse(validateFactories.getIfAvailable(), packageConfig);
    }

    @Bean
    public ApidocImportBeanDefinitionRegistrar apidocImportBeanDefinitionRegistrar() {
        return new ApidocImportBeanDefinitionRegistrar();
    }

    @Bean
    public ControllerHandle controllerHandle(ObjectProvider<EnableApidocConfig> enableApidocConfig) {
        return new ControllerHandle(enableApidocConfig.getIfAvailable());
    }

    @Bean
    public EnumHandle enumHandle(ObjectProvider<EnableApidocConfig> enableApidocConfig) {
        return new EnumHandle(enableApidocConfig.getIfAvailable());
    }

    @Bean
    public InfoHandle infoHandle(ObjectProvider<EnableApidocConfig> enableApidocConfig) {
        return new InfoHandle(enableApidocConfig.getIfAvailable());
    }

    @Bean
    public MethodFieldHandle methodFieldHandle(ObjectProvider<EnableApidocConfig> enableApidocConfig) {
        return new MethodFieldHandle(enableApidocConfig.getIfAvailable());
    }

    @Bean
    public MethodHandle methodHandle(ObjectProvider<EnableApidocConfig> enableApidocConfig) {
        return new MethodHandle(enableApidocConfig.getIfAvailable());
    }

    @Bean
    public ParamHandle paramHandle(ObjectProvider<EnableApidocConfig> enableApidocConfig) {
        return new ParamHandle(enableApidocConfig.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public AnnotationParamGroupStrategy annotationParamGroupStrategy(
            ObjectProvider<IValidateFactory> validateFactory) {
        return new AnnotationParamGroupStrategy(validateFactory.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public YamlParamGroupStrategy yamlParamGroupStrategy(MethodFieldHandle methodFieldHandle,
                                                         EnableApidocConfig enableApidocConfig,
                                                         CsapDocConfig csapDocConfig,
                                                         ObjectProvider<IValidateFactory> validateFactory,
                                                         ApplicationContext applicationContext) {
        return new YamlParamGroupStrategy(methodFieldHandle,
                enableApidocConfig,
                csapDocConfig,
                validateFactory.getIfAvailable(),
                applicationContext);
    }

    @Bean
    public SQLiteParamGroupStrategy sqLiteParamGroupStrategy(
            ObjectProvider<IValidateFactory> validateFactory) {
        return new SQLiteParamGroupStrategy(validateFactory.getIfAvailable());
    }

    @Bean
    public RunApplicationListener runApplicationListener() {
        return new RunApplicationListener();
    }

    @Bean
    @ConditionalOnMissingBean(CsapResourcesProvider.class)
    public CsapResourcesProvider csapResourcesProvider(CsapDocConfig csapDocConfig) {
        return new BaseCsapResourcesProvider().setCsapDocConfig(csapDocConfig);
    }

    @Bean
    @ConditionalOnMissingBean(CsapHeadersProvider.class)
    public CsapHeadersProvider csapHeadersProvider() {
        return new DefaultCsapHeardersProvider();
    }

    @Bean
    public ScannerPackageConfig scannerPackageConfig(CsapDocConfig csapDocConfig,
                                                      ObjectProvider<EnableApidocConfig> enableApidocConfig) {
        EnableApidocConfig enableApidocConfig1 = enableApidocConfig
                .getIfAvailable(EnableApidocConfig::new);
        csapDocConfig.setModelPackages(enableApidocConfig1.getModelPackages());
        csapDocConfig.setEnumPackages(enableApidocConfig1.getEnumPackages());
        return new ScannerPackageConfig(csapDocConfig)
                .addApiClassesList(enableApidocConfig1.getApiPackageClass())
                .addEnumClasseLis(enableApidocConfig1.getEnumPackageClass());
    }

    @ConditionalOnMissingBean(GlobalCsapResponseParamProvider.class)
    @Bean
    public GlobalCsapResponseParamProvider csapResponseParamProvider() {
        return new DefaultGlobalCsapResponseParamProvider();
    }

    @Bean
    @ConditionalOnBean(ApidocStrategy.class)
    public CsapDocController csapDocController(ApplicationContext applicationContext) {
        return new CsapDocController(applicationContext,
                new PostmanConverterService());
    }

    @Bean
    public ApidocContext apidocContext(CsapResourcesProvider resourcesProvider,
                                       ScannerPackageConfig scannerPackageConfig,
                                       CsapDocConfig docConfig) {
        return new ApidocContext(resourcesProvider, scannerPackageConfig, docConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    public DevtoolsClass devtoolsClass() {
        return new DefaultDevtoolsClass();
    }


}
