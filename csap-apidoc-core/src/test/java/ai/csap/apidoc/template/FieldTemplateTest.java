package ai.csap.apidoc.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.validation.type.ValidatePatternType;
import ai.csap.validation.type.ValidationStrategyType;

import cn.hutool.core.io.FileUtil;

class FieldTemplateTest {

    private static final String DIR = "csap-api-doc-template-test";

    @AfterEach
    void cleanUp() {
        FileUtil.del(new File(classpathRoot(), DIR));
    }

    private static String classpathRoot() {
        return URLDecoder.decode(Thread.currentThread().getContextClassLoader().getResource("").getPath(), StandardCharsets.UTF_8);
    }

    @Test
    void parsesTheFormatInTheDesignDocument() {
        String yaml = "version: 1\n"
                + "templates:\n"
                + "  - name: phone-cn\n"
                + "    title: 手机号(中国大陆)\n"
                + "    category: 通用\n"
                + "    description: 手机号\n"
                + "    example: \"13800138000\"\n"
                + "    required: false\n"
                + "    unknown: x\n"
                + "    validate:\n"
                + "      - type: Pattern\n"
                + "        pattern: PATTERN_35\n"
                + "        code: \"400\"\n"
                + "        message: 手机号格式不对\n"
                + "        level: 1\n";
        List<FieldTemplate> list = FieldTemplateCodec.fromYaml(yaml, "test");
        assertEquals(1, list.size());
        FieldTemplate t = list.get(0);
        assertEquals("phone-cn", t.getName());
        assertEquals("13800138000", t.getExample());
        assertEquals(Boolean.FALSE, t.getRequired());
        assertEquals(ValidationStrategyType.Pattern, t.getValidate().get(0).getType());
        assertEquals("PATTERN_35", t.getValidate().get(0).getPattern());
        // 写出再读回
        assertEquals(list, FieldTemplateCodec.fromYaml(FieldTemplateCodec.toYaml(list), "back"));
        assertEquals(list, FieldTemplateCodec.fromJson(FieldTemplateCodec.toJson(list), "back"));
    }

    @Test
    void badInputIsIgnoredNotFatal() {
        assertTrue(FieldTemplateCodec.fromYaml("version: 2\ntemplates:\n  - {name: a, title: A}\n", "future").isEmpty());
        assertTrue(FieldTemplateCodec.fromYaml("{not yaml: [", "broken").isEmpty());
        // 名字不合法、缺 title 的被跳过,重复的后者覆盖前者
        List<FieldTemplate> list = FieldTemplateCodec.fromYaml("version: 1\ntemplates:\n"
                + "  - {name: Bad Name, title: x}\n  - {name: ok1}\n  - {name: dup, title: first}\n  - {name: dup, title: second}\n", "t");
        assertEquals(1, list.size());
        assertEquals("second", list.get(0).getTitle());
    }

    @Test
    void builtinTemplatesAreValidUniqueAndTheirPatternsCompile() {
        List<FieldTemplate> builtin = FieldTemplateService.builtin();
        assertTrue(builtin.size() >= 12 && builtin.size() <= 25, "数量: " + builtin.size());
        Set<String> names = new HashSet<>();
        for (FieldTemplate t : builtin) {
            assertTrue(names.add(t.getName()), "重名: " + t.getName());
            assertNull(FieldTemplateCodec.check(t), t.getName());
            assertFalse(t.getValidate().isEmpty(), t.getName());
            t.getValidate().forEach(v -> {
                assertNotNull(v.getType());
                if (v.getType() == ValidationStrategyType.Pattern) {
                    String p = v.getPattern();
                    String regex = p.startsWith("PATTERN_") ? ValidatePatternType.valueOf(p).getPattern() : p;
                    Pattern.compile(regex);
                    // 两种语言都要能用:不用零宽断言、反向引用
                    // (内置名 PATTERN_nn 由 Java、Go 各自解析成自己的正则,Go 的写法已改成 RE2 可用的)
                    if (!p.startsWith("PATTERN_")) {
                        assertFalse(regex.contains("(?=") || regex.contains("(?!") || regex.contains("(?<"), t.getName());
                    }
                    // 模板的示例必须通过自己的正则(整个值匹配)
                    if (t.getExample() != null) {
                        assertTrue(Pattern.compile(regex).matcher(t.getExample()).matches(), t.getName() + " 示例 " + t.getExample());
                    }
                }
            });
        }
        assertTrue(names.containsAll(Set.of("phone-cn", "email", "id-card-cn", "page-no", "date")));
    }

    @Test
    void yamlFileProjectTemplatesMergeOverBuiltinAndPersist() {
        EnableApidocConfig cfg = new EnableApidocConfig();
        cfg.setPath(DIR);
        cfg.setParamType(ai.csap.apidoc.ApiStrategyType.YAML);
        FieldTemplateService service = new FieldTemplateService(cfg);
        assertTrue(service.projectSupported());
        assertTrue(service.project().isEmpty());

        FieldTemplate mine = new FieldTemplate();
        mine.setName("phone-cn");
        mine.setTitle("手机号(我们的)");
        mine.setDescription("改写的说明");
        service.saveProject(mine);
        FieldTemplate extra = new FieldTemplate();
        extra.setName("order-no");
        extra.setTitle("订单号");
        service.saveProject(extra);

        List<FieldTemplate> all = service.list();
        FieldTemplate phone = all.stream().filter(t -> t.getName().equals("phone-cn")).findFirst().orElseThrow();
        assertEquals("project", phone.getSource());
        assertEquals("改写的说明", phone.getDescription());
        assertEquals("builtin", all.stream().filter(t -> t.getName().equals("email")).findFirst().orElseThrow().getSource());
        assertTrue(all.stream().anyMatch(t -> t.getName().equals("order-no") && "project".equals(t.getSource())));
        // 写到了 classpath 根目录下的文件里
        // (测试里 classpath 根是 target/test-classes,不会被替换成 src/main/resources)
        File written = new File(new File(classpathRoot(), DIR), "field-template/api-field-template.yaml");
        assertTrue(written.isFile(), written.getPath());
        assertTrue(service.deleteProject("order-no"));
        assertFalse(service.deleteProject("email"), "内置模板不能删除");
        assertEquals(1, service.project().size());
    }

    @Test
    void annotationModeHasOnlyBuiltin() {
        EnableApidocConfig cfg = new EnableApidocConfig();
        cfg.setParamType(ai.csap.apidoc.ApiStrategyType.ANNOTATION);
        FieldTemplateService service = new FieldTemplateService(cfg);
        assertFalse(service.projectSupported());
        assertEquals(FieldTemplateService.builtin().size(), service.list().size());
        FieldTemplate t = new FieldTemplate();
        t.setName("x");
        t.setTitle("x");
        org.junit.jupiter.api.Assertions.assertThrows(ai.csap.apidoc.util.ValidateException.class, () -> service.saveProject(t));
    }
}
