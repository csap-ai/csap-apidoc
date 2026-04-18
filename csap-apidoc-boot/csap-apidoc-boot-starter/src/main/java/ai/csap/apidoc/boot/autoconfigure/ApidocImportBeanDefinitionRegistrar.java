package ai.csap.apidoc.boot.autoconfigure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.SQLiteHandle;
import ai.csap.apidoc.annotation.Api;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocBeanDefinitionRegistrar;
import ai.csap.apidoc.util.ApidocClazzUtils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * 自定义 注册.
 * <p>Created on 2020/9/22
 *
 * @author yangchengfu
 * @since 1.0
 */
public class ApidocImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar,
        ApidocBeanDefinitionRegistrar, BeanFactoryAware, EnvironmentAware {
    @Getter
    private BeanFactory beanFactory;

    private Environment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        Map<String, Object> annotationAttributes = importingClassMetadata
                .getAnnotationAttributes(EnableApidoc.class.getName());
        if (annotationAttributes != null) {
            EnableApidocConfig csapDocConfig = registerBean(beanFactory, registry,
                    EnableApidocConfig.class);
            String[] apiPackages = (String[]) annotationAttributes.get("apiPackages");
            String[] enumPackages = (String[]) annotationAttributes.get("enumPackages");
            String[] modelPackages = (String[]) annotationAttributes.get("modelPackages");
            Class<?>[] apiPackageClasses = (Class<?>[]) annotationAttributes.get("apiPackageClasses");
            Class<?>[] enumPackageClasses = (Class<?>[]) annotationAttributes.get("enumPackageClasses");
            Class<?>[] modelPackageClasses = (Class<?>[]) annotationAttributes.get("modelPackageClasses");
            csapDocConfig.setPrefixStrategy((FilePrefixStrategyType) annotationAttributes.get("prefixStrategy"));
            csapDocConfig.setType((ApiStrategyType) annotationAttributes.get("type"));
            // 支持环境变量占位符解析 ${property:defaultValue}
            csapDocConfig.setPath(resolveProperty((String) annotationAttributes.get("path")));
            csapDocConfig.setFileName(resolveProperty((String) annotationAttributes.get("fileName")));
            csapDocConfig.setParamType((ApiStrategyType) annotationAttributes.get("paramType"));
            csapDocConfig.setRequestType((String) annotationAttributes.get("requestType"));
            csapDocConfig.setResponseType((String) annotationAttributes.get("responseType"));
            Set<Class<?>> classSet = ConcurrentHashMap.newKeySet();
            if (ArrayUtil.isNotEmpty(apiPackages)) {
                // 分离出含通配符(**,*,?)与不含通配符的包路径
                ArrayList<String> normalPackages = new ArrayList<>();
                ArrayList<String> patternPackages = new ArrayList<>();
                for (String pkg : apiPackages) {
                    if (containsWildcard(pkg)) {
                        patternPackages.add(pkg.trim());
                    } else {
                        normalPackages.add(pkg.trim());
                    }
                }
                if (!normalPackages.isEmpty()) {
                    Set<Class<?>> aClass = ApidocClazzUtils.getClass(normalPackages, true,
                            this::hasApiAnnotation);
                    if (CollectionUtil.isNotEmpty(aClass)) {
                        classSet.addAll(aClass);
                    }
                }
                for (String pattern : patternPackages) {
                    Set<Class<?>> matched = scanClassesBySpringPattern(pattern);
                    if (CollectionUtil.isNotEmpty(matched)) {
                        classSet.addAll(matched);
                    }
                }
            }
            if (ArrayUtil.isNotEmpty(apiPackageClasses)) {
                classSet.addAll(Arrays.stream(apiPackageClasses)
                        .filter(this::hasApiAnnotation)
                        .collect(Collectors.toList()));
            }
            if (CollectionUtil.isNotEmpty(classSet)) {
                csapDocConfig.addApiPackageClass(StrategyModel.builder()
                        .clazz(classSet)
                        .docType(csapDocConfig.getType())
                        .paramType(csapDocConfig.getParamType())
                        .requestType(csapDocConfig.getRequestType())
                        .fileName(StrUtil.isEmpty(csapDocConfig.getFileName()) ?
                                SQLiteHandle.DEFAULT_DB_NAME : csapDocConfig.getFileName())
                        .responseType(csapDocConfig.getResponseType())
                        .prefixStrategy(csapDocConfig.getPrefixStrategy())
                        .path(csapDocConfig.getPath())
                        .build());
            }
            if (ArrayUtil.isNotEmpty(enumPackages)) {
                csapDocConfig.getEnumPackages().addAll(Arrays.asList(enumPackages));
            }
            if (ArrayUtil.isNotEmpty(modelPackages)) {
                csapDocConfig.getModelPackages().addAll(Arrays.asList(modelPackages));
            }
            if (ArrayUtil.isNotEmpty(enumPackageClasses)) {
                csapDocConfig.getEnumPackageClass().addAll(Arrays.asList(enumPackageClasses));
            }
            if (ArrayUtil.isNotEmpty(modelPackageClasses)) {
                csapDocConfig.getModelPackageClass().addAll(Arrays.asList(modelPackageClasses));
            }
            handleStrategy(csapDocConfig, registry);
        }
    }

    /**
     * 策略处理
     *
     * @param csapDocConfig 文档配置
     * @param registry      注册对象
     */
    public void handleStrategy(EnableApidocConfig csapDocConfig, BeanDefinitionRegistry registry) {
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * 解析属性值，支持环境变量占位符
     * 支持格式：
     * - ${property.name}
     * - ${property.name:defaultValue}
     *
     * @param value 原始值
     * @return 解析后的值
     */
    private String resolveProperty(String value) {
        if (StrUtil.isBlank(value) || environment == null) {
            return value;
        }
        // 使用 Spring Environment 解析占位符
        return environment.resolveRequiredPlaceholders(value);
    }

    private static boolean containsWildcard(String text) {
        if (text == null) {
            return false;
        }
        return text.indexOf('*') >= 0 || text.indexOf('?') >= 0;
    }

    /**
     * 检查类或其实现的接口是否有 @Api 注解
     * 使用 Spring 的 AnnotationUtils.findAnnotation() 方法，支持：
     * - 类本身的注解
     * - 接口上的注解
     * - 父类上的注解
     * - 元注解（meta-annotations）
     *
     * @param clazz 要检查的类
     * @return 如果类本身或其任何接口、父类有 @Api 注解，返回 true
     */
    private boolean hasApiAnnotation(Class<?> clazz) {
        return org.springframework.core.annotation.AnnotationUtils.findAnnotation(clazz, Api.class) != null;
    }

    /**
     * 使用Spring的资源模式扫描类
     * 支持通配符模式，并检查类或其接口是否有 @Api 注解
     *
     * @param packagePattern 包路径模式，支持通配符
     * @return 匹配的类集合
     */
    private Set<Class<?>> scanClassesBySpringPattern(String packagePattern) {
        Set<Class<?>> classes = new HashSet<>();
        if (StrUtil.isBlank(packagePattern)) {
            return classes;
        }
        String base = packagePattern.trim().replace('.', '/');
        if (base.startsWith("/")) {
            base = base.substring(1);
        }
        String resourcePattern = "classpath*:" + base + "/**/*.class";

        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resolver);
        try {
            Resource[] resources = resolver.getResources(resourcePattern);
            if (resources == null || resources.length == 0) {
                return classes;
            }
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                String className = metadataReader.getClassMetadata().getClassName();

                // 先通过元数据快速检查（性能优化）
                boolean hasApiInMetadata = metadataReader.getAnnotationMetadata()
                        .hasAnnotation("ai.csap.apidoc.annotation.Api") ||
                        metadataReader.getAnnotationMetadata()
                                .hasMetaAnnotation("ai.csap.apidoc.annotation.Api");

                // 如果元数据中没有找到，加载类并检查接口
                if (!hasApiInMetadata) {
                    try {
                        Class<?> clazz = Class.forName(className);
                        // 使用 hasApiAnnotation 方法检查类及其接口
                        if (hasApiAnnotation(clazz)) {
                            classes.add(clazz);
                        }
                    } catch (ClassNotFoundException ignore) {
                    }
                } else {
                    // 元数据中找到了，直接加载类
                    try {
                        Class<?> clazz = Class.forName(className);
                        classes.add(clazz);
                    } catch (ClassNotFoundException ignore) {
                    }
                }
            }
        } catch (IOException ignore) {
        }
        return classes;
    }

}
