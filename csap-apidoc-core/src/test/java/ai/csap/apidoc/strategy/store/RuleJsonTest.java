package ai.csap.apidoc.strategy.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.annotation.ApiStatus;
import ai.csap.apidoc.model.ParamGroupMethodProperty;
import ai.csap.validation.factory.Validate;

/**
 * 1.4.0 规则模型的新字段(example、template、mock、status、statusNote)原样存取。
 */
class RuleJsonTest {

    private static ParamGroupMethodProperty.ParamDataValidate field(Object mock) {
        ParamGroupMethodProperty.ParamDataValidate v = new ParamGroupMethodProperty.ParamDataValidate();
        v.setDescription("手机号");
        v.setExample("13800138000");
        v.setTemplate("phone-cn");
        v.setMock(mock);
        v.getValidate().add(new Validate.ConstraintValidatorField().setDescr("格式").setLevel(1));
        return v;
    }

    @Test
    void newFieldsRoundTripWithAnyMockValue() throws Exception {
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("lo", 1);
        object.put("tags", Arrays.asList("a", "b"));
        for (Object mock : new Object[]{"@int(1,100)", "@oneOf(a,b)", "@uuid", 42, 1.5, true, Arrays.asList(1, "x"), object}) {
            ParamGroupMethodProperty p = new ParamGroupMethodProperty();
            p.getRequest().put("phone", field(mock));
            p.setStatus(ApiStatus.DEPRECATED);
            p.setStatusNote("请改用 /v2");
            ParamGroupMethodProperty back = RuleJson.read(RuleJson.write(p));
            ParamGroupMethodProperty.ParamDataValidate f = back.getRequest().get("phone");
            assertEquals(mock, f.getMock(), String.valueOf(mock));
            assertEquals("13800138000", f.getExample());
            assertEquals("phone-cn", f.getTemplate());
            assertEquals(ApiStatus.DEPRECATED, back.getStatus());
            assertEquals("请改用 /v2", back.getStatusNote());
            assertEquals(RuleJson.write(p), RuleJson.write(back), "再次序列化结果一致");
        }
    }

    @Test
    void absentFieldsAreNotWritten() {
        ParamGroupMethodProperty p = new ParamGroupMethodProperty();
        p.getRequest().put("a", new ParamGroupMethodProperty.ParamDataValidate());
        String json = RuleJson.write(p);
        assertFalse(json.contains("mock") || json.contains("example") || json.contains("template")
                || json.contains("status"), json);
    }

    @Test
    void oldAndUnknownContentIsReadWithoutTheNewFields() throws Exception {
        ParamGroupMethodProperty p = RuleJson.read("{\"request\":{\"a\":{\"required\":true}},\"status\":\"FROM_THE_FUTURE\",\"other\":1}");
        assertNull(p.getStatus(), "未知的状态值按没有覆盖处理");
        assertNull(p.getRequest().get("a").getMock());
        assertNull(p.getRequest().get("a").getTemplate());
    }

    @Test
    void statusOnlyRuleIsNotEmpty() {
        ParamGroupMethodProperty p = new ParamGroupMethodProperty();
        assertTrue(RuleJson.isEmpty(p));
        p.setStatus(ApiStatus.FINISH);
        assertFalse(RuleJson.isEmpty(p));
        p.setStatus(null);
        p.setStatusNote("x");
        assertFalse(RuleJson.isEmpty(p));
    }

    @Test
    void copyForUseKeepsEverythingAndSeparatesTheValidateList() {
        ParamGroupMethodProperty.ParamDataValidate source = field("@uuid");
        source.setRequired(true);
        ParamGroupMethodProperty.ParamDataValidate copy = source.copyForUse();
        assertNotSame(source, copy);
        assertEquals("@uuid", copy.getMock());
        assertEquals("phone-cn", copy.getTemplate());
        assertEquals("13800138000", copy.getExample());
        assertEquals(Boolean.TRUE, copy.getRequired());
        copy.getValidate().add(new Validate.ConstraintValidatorField().setDescr("追加"));
        List<?> original = source.getValidate();
        assertEquals(1, original.size());
    }
}
