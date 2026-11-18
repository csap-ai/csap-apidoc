package ai.csap.apidoc.service.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocParameter;
import ai.csap.apidoc.service.schema.DocTypes.Kind;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.type.ModelType;

import com.fasterxml.jackson.databind.JsonNode;

class DocTypesTest {

    @Test
    void kinds() {
        assertEquals(Kind.INTEGER, DocTypes.kind("java.lang.Long"));
        assertEquals(Kind.INTEGER, DocTypes.kind("int32"));
        assertEquals(Kind.NUMBER, DocTypes.kind("BigDecimal"));
        assertEquals(Kind.NUMBER, DocTypes.kind("Number"));
        assertEquals(Kind.BOOLEAN, DocTypes.kind("bool"));
        assertEquals(Kind.DATE, DocTypes.kind("LocalDate"));
        assertEquals(Kind.DATETIME, DocTypes.kind("Time"));
        assertEquals(Kind.TIME, DocTypes.kind("LocalTime"));
        assertEquals(Kind.ARRAY, DocTypes.kind("List<String>"));
        assertEquals(Kind.ARRAY, DocTypes.kind("String[]"));
        assertEquals(Kind.ARRAY, DocTypes.kind("CopyOnWriteArrayList<X>"));
        assertEquals(Kind.STRING, DocTypes.kind("Charset"));
        assertEquals(Kind.STRING, DocTypes.kind("Offset"));
        assertEquals(Kind.OBJECT, DocTypes.kind("Map<String,Object>"));
        assertEquals(Kind.STRING, DocTypes.kind("UserVO"));
        assertEquals(Kind.STRING, DocTypes.kind((String) null));
        assertEquals("Order", DocTypes.elementType("List<Order>"));
        assertEquals("string", DocTypes.elementType("UserVO"));
    }

    @Test
    void coerce() {
        assertEquals(12L, DocTypes.coerce(" 12 ", Kind.INTEGER));
        assertNull(DocTypes.coerce("1.5", Kind.INTEGER));
        assertEquals(1.5, DocTypes.coerce("1.5", Kind.NUMBER));
        assertNull(DocTypes.coerce("NaN", Kind.NUMBER));
        assertNull(DocTypes.coerce("1f", Kind.NUMBER));
        assertEquals(true, DocTypes.coerce("TRUE", Kind.BOOLEAN));
        assertNull(DocTypes.coerce("yes", Kind.BOOLEAN));
        assertEquals("x", DocTypes.coerce("x", Kind.STRING));
    }

    @Test
    void range() {
        assertEquals(2, DocTypes.range("2,8").min);
        assertEquals(8, DocTypes.range("2,8").max);
        assertEquals(5, DocTypes.range("5").max);
        assertEquals(-1, DocTypes.range("3,").max);
        assertNull(DocTypes.range("a,b"));
    }

    @Test
    void paths() {
        CsapDocMethod m = new CsapDocMethod();
        m.setApiPath(new String[]{"/api/"});
        m.setPaths(new String[]{"/users/{id:\\d+}/"});
        assertEquals("/api/users/{id}", DocPaths.fullPath(m, null));
        m.setApiPath(null);
        m.setPaths(new String[]{});
        assertEquals("/", DocPaths.fullPath(m, null));
        assertEquals("GET", DocPaths.verb(m));
    }

    @Test
    void javaDocValidateTypesAreChineseAndStillExport() throws Exception {
        CsapDocParameter p = new CsapDocParameter();
        p.setName("name");
        p.setDataType("String");
        p.setValidate(java.util.Arrays.asList(
                CsapDocParameter.ValidateAttribute.builder().type("不能为空字符串").level(1).build(),
                CsapDocParameter.ValidateAttribute.builder().type("正则表达式").pattern("^a+$").level(1).build()));
        CsapDocModel model = new CsapDocModel();
        model.setModelType(ModelType.OBJECT);
        model.setName("R");
        model.setParameters(java.util.Collections.singletonList(p));
        CsapDocMethod m = new CsapDocMethod();
        m.setKey("k");
        m.setResponse(java.util.Collections.singletonList(model));
        JsonNode s = SchemaExporterGoldenTest.MAPPER.readTree(SchemaExporter.toJson(SchemaExporter.response(m)));
        JsonNode name = s.get("properties").get("name");
        assertEquals(1, name.get("minLength").asInt());
        assertEquals("^a+$", name.get("pattern").asText());
        assertEquals("NotEmpty", name.get("x-csap-validate").get(0).get("type").asText());
        assertEquals("Pattern", name.get("x-csap-validate").get(1).get("type").asText());
    }
}
