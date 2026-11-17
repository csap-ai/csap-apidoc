package ai.csap.apidoc.strategy.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.bind.annotation.RequestMethod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import ai.csap.apidoc.AbstractApidocStrategy;
import ai.csap.apidoc.ApiStrategyType;
import ai.csap.apidoc.FilePrefixStrategyType;
import ai.csap.apidoc.StandardProperties;
import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.autoconfigure.StrategyModel;
import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.strategy.handle.ControllerHandle;
import ai.csap.apidoc.strategy.handle.EnumHandle;
import ai.csap.apidoc.strategy.handle.InfoHandle;
import ai.csap.apidoc.strategy.handle.MethodFieldHandle;
import ai.csap.apidoc.strategy.handle.MethodHandle;
import ai.csap.apidoc.strategy.handle.ParamHandle;

import cn.hutool.core.io.FileUtil;

/**
 * {@code @EnableApidoc(type = YAML/JSON)} 用到的文件型文档策略能正常写入并读回文档,
 * 因此可以在 starter 中按 type 注册。
 */
class FileApidocStrategyRoundTripTest {

    private static final String CONTROLLER = "com.example.UserController";
    private final String dir = "file-strategy-test-" + System.nanoTime();

    @AfterEach
    void cleanUp() {
        FileUtil.del(docDir());
    }

    private AbstractApidocStrategy strategy(ApiStrategyType type, EnableApidocConfig config) {
        ControllerHandle controllerHandle = new ControllerHandle(config);
        EnumHandle enumHandle = new EnumHandle(config);
        InfoHandle infoHandle = new InfoHandle(config);
        MethodHandle methodHandle = new MethodHandle(config);
        MethodFieldHandle methodFieldHandle = new MethodFieldHandle(config);
        ParamHandle paramHandle = new ParamHandle(config);
        return type == ApiStrategyType.YAML
                ? new YamlApidocStrategy(controllerHandle, enumHandle, infoHandle, methodHandle, methodFieldHandle, paramHandle, config)
                : new JsonApidocStrategy(controllerHandle, enumHandle, infoHandle, methodHandle, methodFieldHandle, paramHandle, config);
    }

    private File docDir() {
        String root = URLDecoder.decode(Thread.currentThread().getContextClassLoader().getResource("").getPath(),
                StandardCharsets.UTF_8);
        return new File(root, dir);
    }

    private EnableApidocConfig config(ApiStrategyType type) {
        EnableApidocConfig config = new EnableApidocConfig();
        config.setType(type);
        config.setPath(dir);
        config.setPrefixStrategy(FilePrefixStrategyType.DEFAULT);
        return config;
    }

    private void writeSample(AbstractApidocStrategy strategy) {
        CsapDocModelController controller = new CsapDocModelController();
        controller.setName(CONTROLLER);
        controller.setSimpleName("UserController");
        controller.setValue("用户");
        controller.setMethods(new java.util.HashSet<>(Set.of("get")));
        CsapDocMethod method = new CsapDocMethod();
        method.setKey(CONTROLLER + ".get");
        method.setName("get");
        method.setValue("查询用户");
        method.setDescription("含 \"引号\": 与 # 号");
        method.setMethods(new ArrayList<>(List.of(RequestMethod.GET)));
        Map<String, CsapDocModelController> controllerMap = new HashMap<>();
        controllerMap.put(CONTROLLER, controller);
        Map<String, Map<String, CsapDocMethod>> methodMap = new HashMap<>();
        methodMap.computeIfAbsent(CONTROLLER, k -> new HashMap<>()).put("get", method);

        assertTrue(strategy.write(StandardProperties.builder()
                .controllerMap(controllerMap)
                .methodMap(methodMap)
                .build()));
    }

    private void assertReadBack(AbstractApidocStrategy strategy, ApiStrategyType type) {
        CsapDocResponse response = strategy.apidoc(null, false,
                StrategyModel.builder().docType(type).path(dir).build(), new CsapDocResponse());
        assertEquals(1, response.getApiList().size());
        CsapDocModelController read = response.getApiList().get(0);
        assertEquals(CONTROLLER, read.getName());
        assertEquals("用户", read.getValue());
        assertEquals(1, read.getMethodList().size());
        assertEquals("查询用户", read.getMethodList().get(0).getValue());
        assertEquals("含 \"引号\": 与 # 号", read.getMethodList().get(0).getDescription());
    }

    private List<File> files(String suffix) {
        List<File> files = FileUtil.loopFiles(docDir(), f -> f.getName().endsWith(suffix));
        assertTrue(files.size() >= 2, "应写出 controller、method 两个 " + suffix + " 文件: " + files);
        return files;
    }

    @ParameterizedTest
    @EnumSource(value = ApiStrategyType.class, names = {"YAML", "JSON"})
    void writtenDocumentCanBeReadBack(ApiStrategyType type) {
        EnableApidocConfig config = config(type);
        AbstractApidocStrategy strategy = strategy(type, config);
        assertEquals(type.getName(), strategy.getName());
        writeSample(strategy);
        assertReadBack(strategy, type);
    }

    @Test
    void jsonStrategyWritesRealJson() throws Exception {
        AbstractApidocStrategy strategy = strategy(ApiStrategyType.JSON, config(ApiStrategyType.JSON));
        writeSample(strategy);
        ObjectMapper json = new ObjectMapper();
        for (File file : files(".json")) {
            String content = FileUtil.readUtf8String(file).trim();
            assertTrue(content.startsWith("{") || content.startsWith("["), file.getName() + " 应为 JSON: " + content);
            JsonNode node = json.readTree(content);
            assertFalse(node.isMissingNode());
        }
    }

    @Test
    void jsonStrategyStillReadsLegacyYamlContent() throws Exception {
        AbstractApidocStrategy strategy = strategy(ApiStrategyType.JSON, config(ApiStrategyType.JSON));
        writeSample(strategy);
        // 把文件改写成 1.1.1 及以前的格式:.json 后缀、YAML 内容
        ObjectMapper json = new ObjectMapper();
        YAMLMapper yaml = new YAMLMapper();
        for (File file : files(".json")) {
            String legacy = yaml.writeValueAsString(json.readTree(file));
            assertFalse(legacy.trim().startsWith("{"));
            FileUtil.writeUtf8String(legacy, file);
        }
        assertReadBack(strategy, ApiStrategyType.JSON);

        // 旧文件被再次写入后变成 JSON,且内容保留
        writeSample(strategy);
        for (File file : files(".json")) {
            assertTrue(FileUtil.readUtf8String(file).trim().startsWith("{"));
        }
        assertReadBack(strategy, ApiStrategyType.JSON);
    }
}
