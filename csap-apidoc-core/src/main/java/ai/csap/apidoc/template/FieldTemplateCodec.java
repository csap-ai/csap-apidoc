package ai.csap.apidoc.template;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * 字段模板文件的读写:YAML(文件、jar 内置)和 JSON(数据库 / Redis 里的保留键 {@code @templates})结构相同。
 *
 * @author ycf
 * @since 1.4.0
 */
public final class FieldTemplateCodec {

    private static final Logger LOG = LoggerFactory.getLogger(FieldTemplateCodec.class);

    /**
     * 模板名的写法
     */
    public static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private static final ObjectMapper JSON = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS))
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

    private FieldTemplateCodec() {
    }

    public static List<FieldTemplate> fromYaml(String yaml, String origin) {
        return parse(yaml, YAML, origin);
    }

    public static List<FieldTemplate> fromJson(String json, String origin) {
        return parse(json, JSON, origin);
    }

    private static List<FieldTemplate> parse(String text, ObjectMapper mapper, String origin) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        FieldTemplateFile file;
        try {
            file = mapper.readValue(text, FieldTemplateFile.class);
        } catch (IOException e) {
            LOG.warn("csap-apidoc: 字段模板文件 {} 无法解析,已忽略: {}", origin, e.getMessage());
            return new ArrayList<>();
        }
        if (file == null) {
            return new ArrayList<>();
        }
        if (file.getVersion() > FieldTemplateFile.VERSION) {
            LOG.warn("csap-apidoc: 字段模板文件 {} 的版本 {} 高于当前支持的 {},已忽略整个文件", origin, file.getVersion(),
                    FieldTemplateFile.VERSION);
            return new ArrayList<>();
        }
        Map<String, FieldTemplate> byName = new LinkedHashMap<>();
        for (FieldTemplate t : file.getTemplates()) {
            String problem = check(t);
            if (problem != null) {
                LOG.warn("csap-apidoc: 字段模板文件 {} 里的一个模板被忽略: {}", origin, problem);
                continue;
            }
            if (byName.put(t.getName(), t) != null) {
                LOG.warn("csap-apidoc: 字段模板 {} 在 {} 里重复,后者覆盖前者", t.getName(), origin);
            }
            t.setSource(null);
        }
        return new ArrayList<>(byName.values());
    }

    /**
     * 检查模板,合法返回 null,否则返回原因
     */
    public static String check(FieldTemplate t) {
        if (t == null || t.getName() == null || !NAME.matcher(t.getName()).matches()) {
            return "name 必须是小写字母、数字、- _,最长 64: " + (t == null ? null : t.getName());
        }
        if (t.getTitle() == null || t.getTitle().trim().isEmpty()) {
            return "缺少 title: " + t.getName();
        }
        return null;
    }

    public static String toYaml(List<FieldTemplate> templates) {
        return write(YAML, templates);
    }

    public static String toJson(List<FieldTemplate> templates) {
        return write(JSON, templates);
    }

    private static String write(ObjectMapper mapper, List<FieldTemplate> templates) {
        FieldTemplateFile file = new FieldTemplateFile();
        for (FieldTemplate t : templates) {
            FieldTemplate copy = copy(t);
            copy.setSource(null);
            file.getTemplates().add(copy);
        }
        try {
            return mapper.writeValueAsString(file);
        } catch (IOException e) {
            throw new IllegalStateException("字段模板序列化失败: " + e.getMessage(), e);
        }
    }

    public static FieldTemplate copy(FieldTemplate t) {
        try {
            return JSON.readValue(JSON.writeValueAsString(t), FieldTemplate.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
