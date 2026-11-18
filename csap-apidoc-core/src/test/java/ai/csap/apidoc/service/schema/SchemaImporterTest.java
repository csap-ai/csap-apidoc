package ai.csap.apidoc.service.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.service.schema.SchemaImporter.Change;
import ai.csap.apidoc.service.schema.SchemaImporter.Plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaImporterTest {

    static final ObjectMapper M = SchemaExporterGoldenTest.MAPPER;

    static CsapDocMethod create() throws Exception {
        return M.readValue(new File(SchemaExporterGoldenTest.casesDir(), "user-create.method.json"), CsapDocMethod.class);
    }

    static JsonNode exported() throws Exception {
        return M.readTree(new File(SchemaExporterGoldenTest.casesDir(), "user-create.schema.json"));
    }

    static Change change(Plan p, String key) {
        return p.changes.stream().filter(c -> key.equals(c.keyName)).findFirst().orElse(null);
    }

    @Test
    void exportedSchemaRoundTripsThroughXCsapValidate() throws Exception {
        Plan p = SchemaImporter.plan(create(), true, null, exported().get("request"), true);
        Change name = change(p, "request.name");
        assertEquals("name", name.description);
        assertEquals(true, name.required);
        // x-csap-validate 优先:NotEmpty 保留,Length 是 Java 规则不支持的类型 → 跳过并报告
        assertEquals(1, name.validate.size());
        assertEquals("NotEmpty", name.validate.get(0).type);
        assertEquals("1001", name.validate.get(0).code);
        assertTrue(p.skipped.stream().anyMatch(s -> "Length".equals(s.get("keyword")) && "body.name".equals(s.get("path"))));
        // phone:内置名 PATTERN_35 和正则两条都保留
        Change phone = change(p, "request.phone");
        assertEquals(2, phone.validate.size());
        // 没有规则键的字段不能写规则
        assertTrue(p.unmatched.stream().anyMatch(u -> "body.age".equals(u.get("path"))));
        assertTrue(p.matched >= 2);
    }

    @Test
    void standardKeywordsBecomeRulesWithoutXCsapValidate() throws Exception {
        JsonNode schema = M.readTree("{\"type\":\"object\",\"properties\":{\"body\":{\"type\":\"object\","
                + "\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"d\","
                + "\"examples\":[\"Tom\"],\"pattern\":\"^[A-Z]\",\"x-csap-key\":\"request.name\","
                + "\"x-csap-mock\":\"@uuid\",\"oneOf\":[]}}}}}");
        Plan p = SchemaImporter.plan(create(), true, null, schema, false);
        Change c = change(p, "request.name");
        assertEquals("Tom", c.example);
        assertEquals(true, c.required);
        assertTrue(c.hasMock);
        assertEquals("@uuid", c.mock);
        assertEquals("Pattern", c.validate.get(0).type);
        assertEquals("^[A-Z]", c.validate.get(0).pattern);
        assertTrue(p.skipped.stream().anyMatch(s -> "oneOf".equals(s.get("keyword"))));
        String yaml = SchemaImporter.toYaml("demo.C", "create", p);
        assertTrue(yaml.contains("\"request.name\":"));
        assertTrue(yaml.contains("mock: \"@uuid\""));
    }

    @Test
    void enumAndRangesAreDerived() throws Exception {
        JsonNode schema = M.readTree("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\","
                + "\"minLength\":2,\"maxLength\":8,\"enum\":[\"a\",\"b\"],\"x-csap-key\":\"request.name\"}}}");
        Plan p = SchemaImporter.plan(create(), true, "body", schema, false);
        Change c = change(p, "request.name");
        assertEquals("Length", c.validate.get(0).type);
        assertEquals("2,8", c.validate.get(0).pattern);
        assertEquals("Enum", c.validate.get(1).type);
        assertEquals("a,b", c.validate.get(1).pattern);
    }

    @Test
    void doesNotCreateFieldsAndNeverWritesRequiredFalse() throws Exception {
        JsonNode schema = M.readTree("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},"
                + "\"ghost\":{\"type\":\"string\"}}}");
        Plan p = SchemaImporter.plan(create(), true, "body", schema, true);
        assertNull(change(p, "request.name").required);
        assertEquals(1, p.unmatched.size());
        assertFalse(p.changes.stream().anyMatch(c -> "ghost".equals(c.path)));
    }

    @Test
    void rejectsBadInput() throws Exception {
        CsapDocMethod m = create();
        assertThrows(IllegalArgumentException.class,
                () -> SchemaImporter.plan(m, true, null, M.readTree("{\"type\":\"object\",\"properties\":{\"x\":{}}}"), true));
        assertThrows(IllegalArgumentException.class,
                () -> SchemaImporter.plan(m, true, "nope", M.readTree("{}"), true));
        assertThrows(IllegalArgumentException.class, () -> SchemaImporter.plan(m, true, "body", null, true));
    }

    @Test
    void responseSideWalksFirstModel() throws Exception {
        JsonNode schema = M.readTree("{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"code\":"
                + "{\"description\":\"状态码\"}}}}");
        Plan p = SchemaImporter.plan(create(), false, null, schema, true);
        // 样本里返回字段没有规则键 → 找到了字段但不能写规则,放进 unmatched
        assertEquals(0, p.matched);
        assertTrue(p.unmatched.stream().anyMatch(u -> "code".equals(u.get("path")) && u.get("reason").contains("keyName")));
    }

    @Test
    void chineseValidateTypesAreNormalized() {
        assertEquals("NotNull", DocTypes.validateType("不能为空"));
        assertEquals("NotEmpty", DocTypes.validateType("不能为空字符串"));
        assertEquals("Pattern", DocTypes.validateType("正则表达式"));
        assertEquals("Length", DocTypes.validateType("Length"));
    }
}
