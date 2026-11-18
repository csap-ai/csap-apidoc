package ai.csap.apidoc.template;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import ai.csap.apidoc.util.RuleStorageNotWritableException;

/**
 * YAML 文件模式(YAML / JSON / SQLite 规则存储)下的项目字段模板:{@code {path}/field-template/api-field-template.yaml}。
 * 读取走 classpath(任意 {@code *-field-template.yaml});写入与字段规则一样写回源码目录,打包运行(jar)时不可写,返回同样的提示。
 * 保存后内存里的模板立即更新,不必重启。
 *
 * @author ycf
 * @since 1.4.0
 */
public class YamlFileFieldTemplateStore {

    private static final Logger LOG = LoggerFactory.getLogger(YamlFileFieldTemplateStore.class);
    private static final String DIR = "field-template";
    private static final String FILE = "api-field-template.yaml";

    private final String path;
    private volatile List<FieldTemplate> cache;

    public YamlFileFieldTemplateStore(String path) {
        this.path = path == null || path.trim().isEmpty() ? "csap-api-doc" : path.trim();
    }

    public synchronized List<FieldTemplate> load() {
        if (cache == null) {
            Map<String, FieldTemplate> byName = new LinkedHashMap<>();
            try {
                Resource[] resources = new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:" + path + "/" + DIR + "/*-field-template.yaml");
                for (Resource r : resources) {
                    try (java.io.InputStream in = r.getInputStream()) {
                        String text = new String(readAll(in), StandardCharsets.UTF_8);
                        for (FieldTemplate t : FieldTemplateCodec.fromYaml(text, String.valueOf(r.getDescription()))) {
                            byName.put(t.getName(), t);
                        }
                    }
                }
            } catch (IOException e) {
                LOG.warn("csap-apidoc: 读取字段模板失败: {}", e.getMessage());
            }
            cache = new ArrayList<>(byName.values());
        }
        return new ArrayList<>(cache);
    }

    public synchronized void save(List<FieldTemplate> templates) {
        File file = targetFile();
        try {
            File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("无法创建目录 " + parent);
            }
            Files.write(file.toPath(), FieldTemplateCodec.toYaml(templates).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuleStorageNotWritableException("字段模板文件 " + file.getPath() + " 无法写入(" + e.getMessage() + ")。"
                    + " | Cannot write the field template file " + file.getPath() + " (" + e.getMessage() + ").", e);
        }
        cache = new ArrayList<>(templates);
        LOG.info("csap-apidoc: 字段模板已保存 {}", file.getPath());
    }

    private File targetFile() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        URL root = cl == null ? null : cl.getResource("");
        if (root == null || !"file".equals(root.getProtocol())) {
            throw RuleStorageNotWritableException.classpathNotWritable("YAML", root);
        }
        String base = URLDecoder.decode(root.getPath(), StandardCharsets.UTF_8).replace("target/classes", "src/main/resources");
        return new File(new File(new File(base), path), DIR + File.separator + FILE);
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
