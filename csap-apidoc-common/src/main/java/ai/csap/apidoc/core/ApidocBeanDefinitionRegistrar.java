package ai.csap.apidoc.core;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;

import cn.hutool.core.util.StrUtil;

/**
 * @author yangchengfu
 * @description 自定义注册
 * @dataTime 2020年-12月-05日 21:21:00
 **/
public interface ApidocBeanDefinitionRegistrar {
    /**
     * 获取spring注册工厂
     *
     * @return
     */
    BeanFactory getBeanFactory();

    /**
     * 检查并注册Bean
     *
     * @param registry  注册类
     * @param beanClass 注册的class
     */
    default <T> T registerBean(BeanDefinitionRegistry registry, Class<T> beanClass) {
        return registerBean(getBeanFactory(), registry, StrUtil.lowerFirst(beanClass.getSimpleName()), beanClass);
    }

    /**
     * 检查并注册Bean
     *
     * @param registry  注册类
     * @param beanClass 注册的class
     */
    default <T> T registerBean(BeanFactory beanFactory, BeanDefinitionRegistry registry, Class<T> beanClass) {
        return registerBean(beanFactory, registry, StrUtil.lowerFirst(beanClass.getSimpleName()), beanClass);
    }

    /**
     * 检查并注册Bean
     *
     * @param beanFactory spring bean工厂
     * @param registry    注册类
     * @param name        注册名称
     * @param beanClass   注册的class
     */
    default <T> T registerBean(BeanFactory beanFactory, BeanDefinitionRegistry registry, String name, Class<T> beanClass) {
        if (!beanFactory.containsBean(name)) {
            registry.registerBeanDefinition(name, new RootBeanDefinition(beanClass));
        }
        return beanFactory.getBean(beanClass);
    }
}
