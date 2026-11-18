package ai.csap.apidoc.mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import ai.csap.apidoc.model.CsapDocModel;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mock 生成规则的黄金样本:docs/design/1.4.0-mock-golden/cases/*.input.json → *.expected.json,按 JSON 值比较。
 */
class MockGeneratorGoldenTest {

    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static File casesDir() {
        File d = new File("../docs/design/1.4.0-mock-golden/cases");
        assertTrue(d.isDirectory(), "找不到黄金样本目录 " + d.getAbsolutePath());
        return d;
    }

    static JsonNode run(File input) throws Exception {
        JsonNode c = MAPPER.readTree(input);
        List<CsapDocModel> response = new ArrayList<>();
        for (JsonNode m : c.path("response")) {
            response.add(MAPPER.treeToValue(m, CsapDocModel.class));
        }
        MockGenerator g = new MockGenerator(c.path("seed").asText("csap"), c.path("arraySize").asInt(2),
                c.path("maxDepth").asInt(6), c.path("heuristics").asBoolean(true));
        JsonNode out = g.generate(response.get(0));
        // 经文本再解析一次,整数统一成同一种节点类型
        return MAPPER.readTree(MAPPER.writeValueAsString(out));
    }

    @Test
    void goldenCasesMatch() throws Exception {
        List<File> files = Arrays.stream(casesDir().listFiles()).filter(f -> f.getName().endsWith(".input.json"))
                .sorted().collect(Collectors.toList());
        assertTrue(files.size() >= 5);
        for (File f : files) {
            JsonNode expected = MAPPER.readTree(new File(f.getPath().replace(".input.json", ".expected.json")));
            assertEquals(expected, run(f), f.getName());
        }
    }

    @Test
    void differentSeedChangesDataNotShape() throws Exception {
        JsonNode a = run(new File(casesDir(), "user-detail.input.json"));
        JsonNode b = run(new File(casesDir(), "user-detail-seed2.input.json"));
        assertNotEquals(a, b);
        assertEquals(a.get("data").fieldNames().next(), b.get("data").fieldNames().next());
        assertEquals(a.get("data").size(), b.get("data").size());
    }

    @Test
    void randomRoundTripIsStable() {
        assertEquals(MockRandom.of("csap", "$.a"), MockRandom.of("csap", "$.a"));
        assertNotEquals(MockRandom.of("csap", "$.a"), MockRandom.of("csap", "$.b"));
    }
}
