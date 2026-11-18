package ai.csap.apidoc.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.strategy.handle.MethodFieldHandle;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;

/**
 * YAML 规则缓存后,多次生成文档不能把注解规则重复追加到缓存的 YAML 规则里。
 */
class YamlParamGroupStrategyRequestGroupTest {

    private static final String CLASS_NAME = "com.example.UserController";
    private static final String METHOD_NAME = "add";

    @SuppressWarnings("unused")
    private String name;

    @Test
    void repeatedScanDoesNotAccumulateRules() throws Exception {
        ParamGroupMethodProperty.ParamDataValidate yamlRule = new ParamGroupMethodProperty.ParamDataValidate();
        yamlRule.setRequired(true);
        yamlRule.setInclude(true);
        yamlRule.getValidate().add(rule("yamlLength", 2));

        ParamGroupMethodProperty methodProperty = new ParamGroupMethodProperty();
        methodProperty.getRequest().put("name", yamlRule);
        Map<String, ParamGroupMethodProperty> methods = new HashMap<>();
        methods.put(METHOD_NAME, methodProperty);
        Map<String, Map<String, ParamGroupMethodProperty>> handle = new HashMap<>();
        handle.put(CLASS_NAME, methods);

        EnableApidocConfig config = new EnableApidocConfig();
        config.setPath("unused");
        config.setPrefixStrategy(FilePrefixStrategyType.DEFAULT);
        MethodFieldHandle methodFieldHandle = new MethodFieldHandle(config) {
            @Override
            public Map<String, Map<String, ParamGroupMethodProperty>> handle(ApidocStrategyName strategyType,
                                                                              String path, String prefixName) {
                return handle;
            }
        };
        YamlParamGroupStrategy strategy = new YamlParamGroupStrategy(methodFieldHandle, config, null,
                annotationFactory(), null);

        StrategyModel model = StrategyModel.builder().id("yaml-request-group-" + System.nanoTime()).build();
        CsapDocMethod docMethod = new CsapDocMethod();
        docMethod.setClassName(CLASS_NAME);
        docMethod.setName(METHOD_NAME);
        Field field = getClass().getDeclaredField("name");

        List<String> first = null;
        for (int i = 0; i < 3; i++) {
            ParamGroupMethodProperty.ParamDataValidate result =
                    strategy.paramRequestGroup(model, "name", docMethod, null, field);
            List<String> descr = result.getValidate().stream()
                                       .map(Validate.ConstraintValidatorField::getDescr)
                                       .collect(Collectors.toList());
            // 合并语义不变:YAML 规则 + 注解规则,按 level 排序
            assertEquals(List.of("annotationNotNull", "yamlLength"), descr);
            assertEquals(Boolean.TRUE, result.getRequired());
            assertNotSame(yamlRule, result);
            if (first == null) {
                first = descr;
            }
            assertEquals(first, descr);
        }
        // 缓存里的 YAML 规则保持原样
        assertEquals(1, yamlRule.getValidate().size());
        assertEquals("yamlLength", yamlRule.getValidate().get(0).getDescr());
    }

    private static Validate.ConstraintValidatorField rule(String descr, int level) {
        return new Validate.ConstraintValidatorField().setDescr(descr).setLevel(level);
    }

    /**
     * 每次调用都返回一条新的注解规则,和真实工厂一致
     */
    private static IValidateFactory annotationFactory() {
        return (IValidateFactory) Proxy.newProxyInstance(IValidateFactory.class.getClassLoader(),
                new Class<?>[]{IValidateFactory.class}, (proxy, method, args) -> {
                    if ("getAllFieldConstraintValidator".equals(method.getName())) {
                        List<Validate.ConstraintValidatorField> list = new ArrayList<>();
                        list.add(rule("annotationNotNull", 1));
                        return list;
                    }
                    if ("toString".equals(method.getName())) {
                        return "annotationFactory";
                    }
                    return null;
                });
    }
}
