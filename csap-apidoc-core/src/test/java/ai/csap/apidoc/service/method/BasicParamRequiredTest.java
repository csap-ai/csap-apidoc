package ai.csap.apidoc.service.method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;

import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.annotation.ApiProperty;
import ai.csap.apidoc.annotation.ParamType;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.strategy.ParamGroupStrategy;
import ai.csap.apidoc.util.ApidocClazzUtils;
import ai.csap.validation.factory.IValidateFactory;
import ai.csap.validation.factory.Validate;

/**
 * 基础类型参数的必填:规则来源里有这个参数的规则时以规则为准,没有规则时才用参数上的 {@link ApiProperty#required()};
 * 文档显示与运行时校验登记一致。
 */
class BasicParamRequiredTest {

    @SuppressWarnings("unused")
    static class Target {
        public void annotated(@ApiProperty(value = "编号", required = true) String id) {
        }

        public void plain(String id) {
        }

        public void path(@PathVariable String id) {
        }
    }

    private ParamGroupStrategy strategy;
    private IValidateFactory validateFactory;
    private DefaultAbstractMethod subject;
    private Map<String, ParamGroupStrategy> strategyMap;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        strategy = mock(ParamGroupStrategy.class);
        validateFactory = mock(IValidateFactory.class);
        subject = new DefaultAbstractMethod(validateFactory, null);
        java.lang.reflect.Field f = ApidocContext.class.getDeclaredField("STRATEGY_PARAM_MAP");
        f.setAccessible(true);
        strategyMap = (Map<String, ParamGroupStrategy>) f.get(null);
        strategyMap.put(ApiStrategyType.YAML.getName(), strategy);
    }

    @AfterEach
    void tearDown() {
        strategyMap.remove(ApiStrategyType.YAML.getName());
    }

    private CsapDocParameter run(String methodName, ParamGroupMethodProperty.ParamDataValidate rule) throws Exception {
        Method method = Target.class.getMethod(methodName, String.class);
        Parameter parameter = method.getParameters()[0];
        when(strategy.requestBasicParams(any(), anyString(), any(), any(), anyString(), any())).thenReturn(rule);
        CsapDocMethod docMethod = new CsapDocMethod();
        docMethod.setKey("k." + methodName);
        StrategyModel strategyModel = StrategyModel.builder().paramType(ApiStrategyType.YAML).build();
        return subject.methodBasicParams(ApidocClazzUtils.getRawClassType(String.class, null), docMethod, "id",
                parameter, method, strategyModel).getParameters().get(0);
    }

    private static ParamGroupMethodProperty.ParamDataValidate rule(boolean required) {
        return new ParamGroupMethodProperty.ParamDataValidate(required, null, null, null, null);
    }

    @Test
    void ruleNotRequiredOverridesAnnotationRequired() throws Exception {
        CsapDocParameter p = run("annotated", rule(false));
        assertFalse(p.getRequired());
        verify(validateFactory, never()).addRequestValidate(anyString(), anyString(), anyString(), anyString(),
                any(), anyList());
    }

    @Test
    void ruleRequiredWithoutAnnotationRegistersRuleValidators() throws Exception {
        ParamGroupMethodProperty.ParamDataValidate rule = rule(true);
        List<Validate.ConstraintValidatorField> validators = rule.getValidate();
        CsapDocParameter p = run("plain", rule);
        assertTrue(p.getRequired());
        verify(validateFactory).addRequestValidate(eq("k.plain"), eq("String"), eq("id"), anyString(), any(),
                same(validators));
    }

    @Test
    void noRuleFallsBackToAnnotationAndRegistersValidation() throws Exception {
        CsapDocParameter p = run("annotated", null);
        assertTrue(p.getRequired());
        verify(validateFactory).addRequestValidate(eq("k.annotated"), eq("String"), eq("id"), eq("编号"), any(),
                any());
    }

    @Test
    void noRuleNoAnnotationIsOptionalAndPathVariableIsPath() throws Exception {
        CsapDocParameter p = run("path", null);
        assertFalse(p.getRequired());
        assertEquals(ParamType.PATH, p.getParamType());
        verify(validateFactory, never()).addRequestValidate(anyString(), anyString(), anyString(), anyString(),
                any(), any());
    }
}
