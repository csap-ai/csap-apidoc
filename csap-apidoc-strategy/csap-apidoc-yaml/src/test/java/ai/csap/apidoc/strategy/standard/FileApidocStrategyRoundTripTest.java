package ai.csap.apidoc.strategy.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.bind.annotation.RequestMethod;

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
        String root = URLDecoder.decode(Thread.currentThread().getContextClassLoader().getResource("").getPath(),
                StandardCharsets.UTF_8);
        FileUtil.del(new File(root, dir));
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

    @ParameterizedTest
    @EnumSource(value = ApiStrategyType.class, names = {"YAML", "JSON"})
    void writtenDocumentCanBeReadBack(ApiStrategyType type) {
        EnableApidocConfig config = new EnableApidocConfig();
        config.setType(type);
        config.setPath(dir);
        config.setPrefixStrategy(FilePrefixStrategyType.DEFAULT);
        AbstractApidocStrategy strategy = strategy(type, config);
        assertEquals(type.getName(), strategy.getName());

        CsapDocModelController controller = new CsapDocModelController();
        controller.setName(CONTROLLER);
        controller.setSimpleName("UserController");
        controller.setValue("用户");
        controller.setMethods(new java.util.HashSet<>(Set.of("get")));
        CsapDocMethod method = new CsapDocMethod();
        method.setKey(CONTROLLER + ".get");
        method.setName("get");
        method.setValue("查询用户");
        method.setMethods(new ArrayList<>(List.of(RequestMethod.GET)));
        Map<String, CsapDocModelController> controllerMap = new HashMap<>();
        controllerMap.put(CONTROLLER, controller);
        Map<String, Map<String, CsapDocMethod>> methodMap = new HashMap<>();
        methodMap.computeIfAbsent(CONTROLLER, k -> new HashMap<>()).put("get", method);

        assertTrue(strategy.write(StandardProperties.builder()
                .controllerMap(controllerMap)
                .methodMap(methodMap)
                .build()));

        CsapDocResponse response = strategy.apidoc(null, false,
                StrategyModel.builder().docType(type).path(dir).build(), new CsapDocResponse());
        assertEquals(1, response.getApiList().size());
        CsapDocModelController read = response.getApiList().get(0);
        assertEquals(CONTROLLER, read.getName());
        assertEquals("用户", read.getValue());
        assertEquals(1, read.getMethodList().size());
        assertEquals("查询用户", read.getMethodList().get(0).getValue());
    }
}
