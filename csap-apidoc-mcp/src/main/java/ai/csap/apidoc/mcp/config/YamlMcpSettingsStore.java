package ai.csap.apidoc.mcp.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.springframework.core.io.Resource;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * YAML 规则文件({path}/mcp.yaml)。可写文件或只读的 classpath 资源二选一。
 *
 * @author ycf
 * @since 1.3.0
 */
public class YamlMcpSettingsStore implements McpSettingsStore {

    static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final File file;
    private final Resource readOnly;
    private McpSettings readOnlyOverride;

    /**
     * @param file     可写文件(为 null 时只读)
     * @param readOnly 只读资源(file 为 null 时使用)
     */
    public YamlMcpSettingsStore(File file, Resource readOnly) {
        this.file = file;
        this.readOnly = readOnly;
    }

    @Override
    public synchronized McpSettings load() {
        if (file == null && readOnlyOverride != null) {
            return readOnlyOverride.copy();
        }
        try {
            if (file != null) {
                if (!file.isFile() || file.length() == 0) {
                    return McpSettings.empty();
                }
                McpSettings s = YAML.readValue(file, McpSettings.class);
                return s == null ? McpSettings.empty() : s;
            }
            if (readOnly != null && readOnly.exists()) {
                try (InputStream in = readOnly.getInputStream()) {
                    McpSettings s = YAML.readValue(in, McpSettings.class);
                    return s == null ? McpSettings.empty() : s;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取 MCP 规则文件失败: " + location() + ": " + e.getMessage(), e);
        }
        return McpSettings.empty();
    }

    @Override
    public synchronized void save(McpSettings settings) throws IOException {
        if (file == null) {
            readOnlyOverride = settings.copy();
            return;
        }
        File dir = file.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("无法创建目录 " + dir);
        }
        // 先写临时文件再替换,避免写到一半被读到
        File tmp = new File(dir, file.getName() + ".tmp");
        String header = "# csap-apidoc MCP 设置(DevTools \"MCP\" 页面维护,也可手工编辑)\n";
        Files.write(tmp.toPath(), (header + YAML.writeValueAsString(settings)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public boolean isPersistent() {
        return file != null;
    }

    @Override
    public String type() {
        return "yaml";
    }

    @Override
    public String location() {
        if (file != null) {
            return file.getPath();
        }
        return readOnly == null ? "(none)" : readOnly.getDescription() + " (只读)";
    }
}
