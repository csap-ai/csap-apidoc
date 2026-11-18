package ai.csap.validation.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.csap.validation.ValidationViolationException;
import ai.csap.validation.factory.Validate;
import ai.csap.validation.properties.OptionalFieldRules;
import ai.csap.validation.properties.ValidationProperties;
import ai.csap.validation.type.ValidationStrategyType;

/**
 * 请求参数校验黄金用例(docs/conformance/validation-cases.json,Java 与 Go 共用):
 * 非必填字段没传值不校验、传了值就校验。用例的取值方式见 docs/design/1.4.0-validation-alignment.md 第 5 节。
 */
class ValidationConformanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode load() throws Exception {
        File f = new File(URLDecoder.decode(ValidationConformanceTest.class.getResource("/").getPath(), StandardCharsets.UTF_8));
        // 从测试 classpath 向上找仓库根下的 docs/conformance
        for (File dir = f; dir != null; dir = dir.getParentFile()) {
            File file = new File(dir, "docs/conformance/validation-cases.json");
            if (file.isFile()) {
                return MAPPER.readTree(file);
            }
        }
        throw new IllegalStateException("找不到 docs/conformance/validation-cases.json");
    }

    // ------------------------------------------------------------------ 取值

    /**
     * 请求里这个字段的取值;null 表示没传。BODY 经过数组时每个元素一个值,上级为 null / 缺失时没有值(不级联)。
     */
    private static List<Object> values(JsonNode field, JsonNode request) {
        String in = field.get("in").asText();
        List<Object> out = new ArrayList<>();
        switch (in) {
            case "QUERY": {
                String name = field.get("name").asText();
                String raw = request.path("query").asText("");
                Object v = null;
                for (String pair : raw.isEmpty() ? new String[0] : raw.split("&")) {
                    int eq = pair.indexOf('=');
                    String k = eq < 0 ? pair : pair.substring(0, eq);
                    if (k.equals(name)) {
                        v = eq < 0 ? "" : pair.substring(eq + 1);
                        break;
                    }
                }
                out.add(v);
                break;
            }
            case "HEADER": {
                JsonNode h = request.path("headers").get(field.get("name").asText());
                out.add(h == null ? null : h.asText());
                break;
            }
            case "PATH": {
                JsonNode p = request.path("pathParams").get(field.get("name").asText());
                out.add(p == null || p.asText().isEmpty() ? null : p.asText());
                break;
            }
            default: {
                List<JsonNode> cur = new ArrayList<>();
                cur.add(request.get("body"));
                String[] path = field.get("path").asText().split("\\.");
                for (int i = 0; i < path.length; i++) {
                    List<JsonNode> next = new ArrayList<>();
                    for (JsonNode c : cur) {
                        for (JsonNode item : expand(c)) {
                            if (item == null || !item.isObject()) {
                                continue;
                            }
                            JsonNode x = item.get(path[i]);
                            if (x == null || x.isNull()) {
                                if (i == path.length - 1) {
                                    next.add(null);
                                }
                                continue;
                            }
                            next.add(x);
                        }
                    }
                    cur = next;
                }
                for (JsonNode c : cur) {
                    out.add(c == null ? null : MAPPER.convertValue(c, Object.class));
                }
            }
        }
        return out;
    }

    private static List<JsonNode> expand(JsonNode n) {
        List<JsonNode> out = new ArrayList<>();
        if (n != null && n.isArray()) {
            n.forEach(e -> out.addAll(expand(e)));
        } else {
            out.add(n);
        }
        return out;
    }

    // ------------------------------------------------------------------ 构造校验项

    private static Validate.ValidateField validateField(JsonNode field) {
        List<Validate.ConstraintValidatorField> rules = new ArrayList<>();
        for (JsonNode r : field.path("rules")) {
            ValidationStrategyType type = ValidationStrategyType.valueOf(r.get("type").asText());
            rules.add(new Validate.ConstraintValidatorField()
                    .setType(type).setCode(r.path("code").asText(null)).setMessage(r.path("message").asText(null))
                    .setPattern(type == ValidationStrategyType.Pattern ? r.path("pattern").asText(null) : null)
                    .setLevel(r.path("level").asInt(1)));
        }
        // 与文档生成时一致:按 level 升序,同级保持书写顺序(List.sort 是稳定排序)
        rules.sort(Comparator.comparingInt(Validate.ConstraintValidatorField::getLevel));
        String name = field.has("name") ? field.get("name").asText() : field.get("path").asText();
        return new Validate.ValidateField().fieldName(name).remark(name).constraintValidators(rules)
                                           .required(field.path("required").asBoolean(false));
    }

    /**
     * 跑一个用例,返回 null 表示通过,否则返回 "规则/码"
     */
    private static String run(JsonNode c, ValidationProperties properties) {
        Validate.ValidateField vf = validateField(c.get("field"));
        for (Object v : values(c.get("field"), c.get("request"))) {
            try {
                vf.validators(v, properties);
            } catch (ValidationViolationException e) {
                return e.getRule() + "/" + e.getCode();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 测试

    @TestFactory
    Stream<DynamicTest> goldenCases() throws Exception {
        JsonNode doc = load();
        assertNotNull(doc.get("cases"));
        List<JsonNode> cases = new ArrayList<>();
        doc.get("cases").forEach(cases::add);
        assertTrue(cases.size() >= 70, "黄金用例数量: " + cases.size());
        ValidationProperties properties = new ValidationProperties();
        return cases.stream().map(c -> DynamicTest.dynamicTest(c.get("id").asText(), () -> {
            JsonNode expect = c.get("expect");
            String got = run(c, properties);
            if (expect.get("ok").asBoolean()) {
                assertEquals(null, got, c.get("desc").asText());
            } else {
                assertEquals(expect.get("rule").asText() + "/" + expect.get("code").asText(), got, c.get("desc").asText());
            }
        }));
    }

    /**
     * skip 开关恢复 1.3.0 及以前的行为:非必填字段一律不校验(传了不合法的值也放行);必填字段不受影响。
     */
    @Test
    void skipSwitchRestoresTheOldBehaviour() throws Exception {
        ValidationProperties skip = new ValidationProperties();
        skip.setOptionalFieldRules(OptionalFieldRules.SKIP);
        int skipped = 0;
        int requiredChecked = 0;
        for (JsonNode c : load().get("cases")) {
            boolean required = c.get("field").path("required").asBoolean(false);
            String got = run(c, skip);
            if (!required) {
                assertEquals(null, got, "skip 模式下非必填字段不校验: " + c.get("id").asText());
                skipped++;
            } else {
                String expected = c.get("expect").get("ok").asBoolean() ? null
                        : c.get("expect").get("rule").asText() + "/" + c.get("expect").get("code").asText();
                assertEquals(expected, got, "skip 模式下必填字段照常校验: " + c.get("id").asText());
                requiredChecked++;
            }
        }
        assertTrue(skipped > 20 && requiredChecked > 15, skipped + " / " + requiredChecked);
    }

    @Test
    void defaultIsValidate() {
        assertEquals(OptionalFieldRules.VALIDATE, new ValidationProperties().getOptionalFieldRules());
    }
}
