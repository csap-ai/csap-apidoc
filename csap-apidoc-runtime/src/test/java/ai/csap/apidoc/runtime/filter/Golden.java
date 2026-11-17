package ai.csap.apidoc.runtime.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黄金样本:优化前的实现生成的输出,优化后必须逐字节一致。
 * <p>{@code -Dcsap.golden.write=true} 时重新生成(只应在改变输出的提交里使用)。
 */
public final class Golden {

    private Golden() {
    }

    public static boolean writing() {
        return Boolean.getBoolean("csap.golden.write");
    }

    private static Path file(String name) {
        return Paths.get(System.getProperty("user.dir"), "src", "test", "resources", "golden", name + ".tsv");
    }

    /**
     * 读取样本(文件不存在时返回空 Map)
     */
    public static Map<String, String> load(String name) {
        Map<String, String> out = new LinkedHashMap<>();
        Path path = file(name);
        if (!Files.exists(path)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    out.put(line.substring(0, tab), line.substring(tab + 1));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /**
     * 写入或比对一组样本
     */
    public static void verify(String name, Map<String, String> actual) {
        if (writing()) {
            List<String> lines = new ArrayList<>();
            actual.forEach((k, v) -> {
                if (v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
                    throw new IllegalStateException("样本含换行: " + k);
                }
                lines.add(k + '\t' + v);
            });
            try {
                Files.createDirectories(file(name).getParent());
                Files.write(file(name), lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return;
        }
        Map<String, String> expected = load(name);
        assertTrue(!expected.isEmpty(), "缺少黄金样本 " + file(name));
        assertEquals(expected.keySet(), actual.keySet(), "样本集合不一致");
        List<String> diff = new ArrayList<>();
        expected.forEach((k, v) -> {
            if (!v.equals(actual.get(k))) {
                diff.add(k + "\n  want " + v + "\n  got  " + actual.get(k));
            }
        });
        assertTrue(diff.isEmpty(), diff.size() + " 个样本与黄金样本不一致:\n" + String.join("\n", diff));
    }
}
