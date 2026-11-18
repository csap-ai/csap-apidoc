package ai.csap.apidoc.service.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON Schema 导出的黄金样本:docs/design/1.4.0-mock-golden/cases/*.method.json → *.schema.json
 */
class SchemaExporterGoldenTest {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static File casesDir() {
        File d = new File("../docs/design/1.4.0-mock-golden/cases");
        assertTrue(d.isDirectory(), "找不到黄金样本目录 " + d.getAbsolutePath());
        return d;
    }

    @Test
    void goldenCasesMatch() throws Exception {
        List<File> files;
        try (Stream<File> s = Stream.of(casesDir().listFiles())) {
            files = s.filter(f -> f.getName().endsWith(".method.json")).sorted().collect(Collectors.toList());
        }
        assertTrue(files.size() >= 2);
        for (File f : files) {
            CsapDocMethod m = MAPPER.readValue(f, CsapDocMethod.class);
            JsonNode expected = MAPPER.readTree(new File(f.getPath().replace(".method.json", ".schema.json")));
            JsonNode actual = MAPPER.readTree(SchemaExporter.toJson(SchemaExporter.method(m, null)));
            assertEquals(expected, actual, f.getName());
        }
    }

    @Test
    void bundleReferencesDefs() throws Exception {
        CsapDocMethod m = MAPPER.readValue(new File(casesDir(), "user-create.method.json"), CsapDocMethod.class);
        CsapDocMethod hidden = MAPPER.readValue(new File(casesDir(), "user-list.method.json"), CsapDocMethod.class);
        hidden.setKey("demo.Hidden");
        hidden.setHidden(true);
        CsapDocModelController c = new CsapDocModelController();
        c.setMethodList(new ArrayList<>(java.util.Arrays.asList(m, hidden)));
        CsapDocResponse doc = new CsapDocResponse();
        doc.getApiList().add(c);
        JsonNode b = MAPPER.readTree(SchemaExporter.toJson(SchemaExporter.bundle(doc)));
        assertEquals(SchemaExporter.DRAFT, b.get("$schema").asText());
        assertEquals(1, b.get("x-csap-operations").size());
        JsonNode op = b.get("x-csap-operations").get(0);
        assertEquals("#/$defs/demo.UserController.create.request", op.get("request").asText());
        assertTrue(b.get("$defs").has("demo.UserController.create.request"));
        assertTrue(b.get("$defs").has("demo.UserController.create.response"));
        assertTrue(!b.get("$defs").get("demo.UserController.create.request").has("$schema"));
    }
}
