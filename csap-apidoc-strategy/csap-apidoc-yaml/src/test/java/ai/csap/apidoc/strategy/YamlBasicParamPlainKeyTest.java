package ai.csap.apidoc.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.core.ApidocStrategyName;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.strategy.handle.MethodFieldHandle;

/**
 * 单值参数(@RequestParam / @PathVariable)的规则键:优先 "<名>.<名>",没有时回退纯 "<名>"
 * (早期版本写出的规则文件用纯名);纯名只在请求规则里找。
 */
class YamlBasicParamPlainKeyTest {

    private static final String CLASS_NAME = "com.example.UserController";
    private static final String METHOD_NAME = "list";

    @SuppressWarnings("unused")
    static class Target {
        public void list(String limit, String id, String data) {
        }
    }

    private static ParamGroupMethodProperty.ParamDataValidate rule(String description) {
        ParamGroupMethodProperty.ParamDataValidate r = new ParamGroupMethodProperty.ParamDataValidate();
        r.setRequired(false);
        r.setDescription(description);
        return r;
    }

    private ParamGroupMethodProperty.ParamDataValidate lookup(ParamGroupMethodProperty property, int index)
            throws Exception {
        Map<String, ParamGroupMethodProperty> methods = new HashMap<>();
        methods.put(METHOD_NAME, property);
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
        YamlParamGroupStrategy strategy = new YamlParamGroupStrategy(methodFieldHandle, config, null, null, null);
        StrategyModel model = StrategyModel.builder().id("yaml-basic-plain-" + System.nanoTime()).build();
        CsapDocMethod docMethod = new CsapDocMethod();
        docMethod.setClassName(CLASS_NAME);
        docMethod.setName(METHOD_NAME);
        Method method = Target.class.getMethod(METHOD_NAME, String.class, String.class, String.class);
        Parameter parameter = method.getParameters()[index];
        String name = new String[]{"limit", "id", "data"}[index];
        return strategy.requestBasicParams(model, name, docMethod, method, name, parameter);
    }

    @Test
    void plainKeyIsUsedWhenFullKeyIsMissing() throws Exception {
        ParamGroupMethodProperty property = new ParamGroupMethodProperty();
        property.getRequest().put("limit", rule("返回数量,最大1000"));
        ParamGroupMethodProperty.ParamDataValidate result = lookup(property, 0);
        assertEquals("返回数量,最大1000", result.getDescription());
    }

    @Test
    void fullKeyWinsOverPlainKey() throws Exception {
        ParamGroupMethodProperty property = new ParamGroupMethodProperty();
        property.getRequest().put("id", rule("纯名"));
        property.getRequest().put("id.id", rule("全名"));
        assertEquals("全名", lookup(property, 1).getDescription());
    }

    @Test
    void plainKeyIsNotTakenFromResponseRules() throws Exception {
        ParamGroupMethodProperty property = new ParamGroupMethodProperty();
        property.getRequest().put("other.other", rule("别的参数"));
        property.getResponse().put("data", rule("返回字段"));
        assertNull(lookup(property, 2));
    }
}
