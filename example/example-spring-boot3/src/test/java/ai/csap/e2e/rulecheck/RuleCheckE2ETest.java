package ai.csap.e2e.rulecheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 启动时字段规则过期检查:方法改名、路径在新结构中不存在 → WARN;检查不改变文档输出和过滤结果。
 */
@ExtendWith(OutputCaptureExtension.class)
class RuleCheckE2ETest {

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    static final String MARK = "字段规则可能已过期";

    static class Snapshot {
        List<String> warns;
        String doc;
        String page;
        String detail;
    }

    static Snapshot run(boolean ruleCheck, CapturedOutput output) throws Exception {
        int from = output.getOut().length();
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(RuleCheckApplication.class)
                .properties("server.port=0", "csap.apidoc.rule-check.enabled=" + ruleCheck,
                        "csap.apidoc.runtime.filter.enabled=true", "spring.main.banner-mode=off")
                .run()) {
            int port = ((ServletWebServerApplicationContext) ctx).getWebServer().getPort();
            Snapshot s = new Snapshot();
            // /csap/apidoc 会等待启动扫描完成;去掉响应里的时间戳再比较
            ObjectNode doc = (ObjectNode) JSON.readTree(get(port, "/csap/apidoc"));
            doc.remove("time");
            stripGeneratedKeys(doc);
            s.doc = JSON.writeValueAsString(doc);
            s.page = get(port, "/e2e/rule-check/items/page");
            s.detail = get(port, "/e2e/rule-check/items/detail?id=7");
            // 再全量扫描一次:结果没变,不重复打日志
            get(port, "/csap/apidoc?flush=true");
            s.warns = Arrays.stream(output.getOut().substring(from).split("\\R"))
                    .filter(l -> l.contains(" WARN ") && l.contains(MARK)).collect(Collectors.toList());
            return s;
        }
    }

    /**
     * 参数节点的 key 是每次生成的随机标识,比较前去掉
     */
    static void stripGeneratedKeys(JsonNode node) {
        if (node.isObject()) {
            if (node.has("position") && node.has("key")) {
                ((ObjectNode) node).remove("key");
            }
            node.forEach(RuleCheckE2ETest::stripGeneratedKeys);
        } else if (node.isArray()) {
            node.forEach(RuleCheckE2ETest::stripGeneratedKeys);
        }
    }

    static String get(int port, String path) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        return r.body();
    }

    @Test
    void warnsAboutStaleRulesWithoutChangingBehaviour(CapturedOutput output) throws Exception {
        Snapshot on = run(true, output);
        // 每个方法汇总成一条
        assertEquals(2, on.warns.size(), String.join("\n", on.warns));
        String renamed = on.warns.stream().filter(w -> w.contains("ItemPageController.listItems")).findFirst().orElse("");
        assertTrue(renamed.contains("类中已没有这个方法"), renamed);
        assertTrue(renamed.contains("相似的现有方法: pageItems"), renamed);
        assertTrue(renamed.contains("规则来源 yaml: csap-rule-check"), renamed);
        String paths = on.warns.stream().filter(w -> w.contains("ItemPageController.pageItems")).findFirst().orElse("");
        assertTrue(paths.contains("返回: data.animationUrl"), paths);
        assertTrue(paths.contains("请求: kw.kw"), paths);
        assertFalse(paths.contains("keyword"), paths);
        // 规则与结构一致的方法不报
        assertTrue(on.warns.stream().noneMatch(w -> w.contains(".detail")), String.join("\n", on.warns));

        // 关闭检查:不打日志;文档与过滤结果逐字节一致
        Snapshot off = run(false, output);
        assertTrue(off.warns.isEmpty(), String.join("\n", off.warns));
        assertTrue(off.doc.equals(on.doc), "开启 / 关闭检查时文档输出不一致");
        assertEquals(off.page, on.page);
        assertEquals(off.detail, on.detail);
        // 过期规则的实际效果:文档里分页结构的字段全被白名单排除,看起来像泛型没解析(检查只提示,不修正)
        assertTrue(on.doc.contains("\"dataType\":\"Page<Item>\""), on.doc);
        assertTrue(on.doc.contains("\"name\":\"ai.csap.e2e.rulecheck.Page\",\"description\":\"\",\"modelType\":\"OBJECT\","
                + "\"group\":[\"default\"],\"version\":[\"default\"],\"parameters\":[]"), on.doc);
        assertEquals("{\"code\":\"0\",\"data\":{\"id\":7,\"name\":\"x\"}}", on.detail);
    }
}
