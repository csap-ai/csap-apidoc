package ai.csap.apidoc.mcp.config;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import ai.csap.apidoc.mcp.autoconfigure.McpProperties;

/**
 * 按配置和字段规则的存储策略选择 MCP 规则存储,位置与字段规则一致:
 * <ul>
 *   <li>配置了 config-file:就用它(可写)</li>
 *   <li>开发时(classpath 根是目录):{@code target/classes} 换成 {@code src/main/resources},写回源码目录</li>
 *   <li>打成 jar:只读 classpath 资源,DevTools 修改只在内存生效</li>
 * </ul>
 *
 * @author ycf
 * @since 1.3.0
 */
public final class McpSettingsStores {

    private static final Logger LOG = LoggerFactory.getLogger(McpSettingsStores.class);

    public static final String YAML_FILE = "mcp.yaml";
    static final String YAML_CLASS = "com.fasterxml.jackson.dataformat.yaml.YAMLFactory";
    static final String SQLITE_CLASS = "org.sqlite.JDBC";

    private McpSettingsStores() {
    }

    /**
     * @param properties     MCP 配置
     * @param rulePath       字段规则目录(@EnableApidoc path,默认 csap-api-doc)
     * @param sqliteRules    字段规则是否使用 SQLite 策略
     * @param sqliteFileName SQLite 库名(不含 .db)
     * @return 存储
     */
    public static McpSettingsStore create(McpProperties properties, String rulePath, boolean sqliteRules,
                                          String sqliteFileName) {
        McpProperties.Store store = properties.getStore();
        String configFile = properties.getConfigFile();
        if (store == McpProperties.Store.AUTO) {
            if (configFile != null && !configFile.trim().isEmpty()) {
                store = configFile.endsWith(".db") ? McpProperties.Store.SQLITE : McpProperties.Store.YAML;
            } else {
                store = sqliteRules ? McpProperties.Store.SQLITE : McpProperties.Store.YAML;
            }
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (store == McpProperties.Store.SQLITE && !ClassUtils.isPresent(SQLITE_CLASS, cl)) {
            LOG.warn("csap-apidoc MCP: 没有 sqlite-jdbc,MCP 设置改为只保存在内存");
            return new MemoryMcpSettingsStore(McpSettings.empty(), "memory (sqlite-jdbc 不在 classpath)");
        }
        if (store == McpProperties.Store.YAML && !ClassUtils.isPresent(YAML_CLASS, cl)) {
            LOG.warn("csap-apidoc MCP: 没有 jackson-dataformat-yaml,MCP 设置改为只保存在内存");
            return new MemoryMcpSettingsStore(McpSettings.empty(), "memory (jackson-dataformat-yaml 不在 classpath)");
        }
        if (store == McpProperties.Store.MEMORY) {
            return new MemoryMcpSettingsStore();
        }
        String dir = rulePath == null || rulePath.trim().isEmpty() ? "csap-api-doc" : trimSlashes(rulePath);
        String name = store == McpProperties.Store.SQLITE
                ? (sqliteFileName == null || sqliteFileName.isEmpty() ? "apidoc" : sqliteFileName) + ".db"
                : YAML_FILE;
        if (configFile != null && !configFile.trim().isEmpty()) {
            File f = new File(configFile.trim());
            return store == McpProperties.Store.SQLITE ? new SqliteMcpSettingsStore(f, true)
                    : new YamlMcpSettingsStore(f, null);
        }
        File local = localFile(cl, dir + "/" + name);
        if (local != null) {
            return store == McpProperties.Store.SQLITE ? new SqliteMcpSettingsStore(local, true)
                    : new YamlMcpSettingsStore(local, null);
        }
        ClassPathResource resource = new ClassPathResource(dir + "/" + name, cl);
        if (store == McpProperties.Store.SQLITE) {
            File copy = copyToTemp(resource);
            if (copy == null) {
                return new MemoryMcpSettingsStore(McpSettings.empty(), "memory (classpath 中没有 " + dir + "/" + name + ")");
            }
            return new SqliteMcpSettingsStore(copy, false);
        }
        return new YamlMcpSettingsStore(null, resource);
    }

    /**
     * 开发环境下规则文件的可写位置;classpath 不是目录(jar)时返回 null
     */
    static File localFile(ClassLoader cl, String relative) {
        URL root = cl == null ? null : cl.getResource("");
        if (root == null || !"file".equals(root.getProtocol())) {
            return null;
        }
        String rootPath = URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8);
        String srcPath = rootPath.replace("target/classes", "src/main/resources");
        File src = new File(srcPath);
        File base = src.isDirectory() ? src : new File(rootPath);
        return new File(base, relative);
    }

    private static File copyToTemp(ClassPathResource resource) {
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            File tmp = File.createTempFile("csap-mcp-", ".db");
            tmp.deleteOnExit();
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (Exception e) {
            LOG.warn("csap-apidoc MCP: 复制 {} 失败: {}", resource.getPath(), e.getMessage());
            return null;
        }
    }

    private static String trimSlashes(String s) {
        String r = s.trim();
        while (r.startsWith("/")) {
            r = r.substring(1);
        }
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }
}
