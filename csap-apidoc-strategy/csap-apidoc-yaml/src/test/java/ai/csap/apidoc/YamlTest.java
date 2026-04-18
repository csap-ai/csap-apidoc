package ai.csap.apidoc;

import java.io.FileInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModel;
import ai.csap.apidoc.model.CsapDocModelController;

import lombok.SneakyThrows;

/**
 * @Author ycf
 * @Date 2021/9/9 4:13 下午
 * @Version 1.0
 */
public class YamlTest {

    @SneakyThrows
    @Test
    public void yamlToObject() {
        ClassLoader classLoader = getClass().getClassLoader();
        String controllerPath = URLDecoder.decode(classLoader.getResource("application-controller.yaml").getPath(), StandardCharsets.UTF_8);
        Map<String, CsapDocModelController> map2 = new YAMLMapper().readValue(new FileInputStream(controllerPath), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        System.out.println(map2);
        String methodPath = URLDecoder.decode(classLoader.getResource("application-method.yaml").getPath(), StandardCharsets.UTF_8);
        Map<String, Map<String, CsapDocMethod>> methodMap = new YAMLMapper().readValue(new FileInputStream(methodPath), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        System.out.println(methodMap);
        String paramPath = URLDecoder.decode(classLoader.getResource("application-param.yaml").getPath(), StandardCharsets.UTF_8);
        Map<String, Map<String, CsapDocModel>> paramMap = new YAMLMapper().readValue(new FileInputStream(paramPath), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        System.out.println(paramMap);

        String enumPath = URLDecoder.decode(classLoader.getResource("application-enum.yaml").getPath(), StandardCharsets.UTF_8);
        Map<String, List<Map<String, Object>>> enumMap = new YAMLMapper().readValue(new FileInputStream(enumPath), new com.fasterxml.jackson.core.type.TypeReference<>() {
        });
        System.out.println(enumMap);
    }

    @Test
    public void jsonToYaml() throws JsonProcessingException {
        // parse JSON
        JsonNode jsonNodeTree = new ObjectMapper().readTree("{\"name\":\"张三\",\"list\":[{\"age\":18},{\"age\":19}],\"obj\":{\"sex\":1,\"info\":{\"name\":\"李四\"}}}");
        String jsonAsYaml = new YAMLMapper().writeValueAsString(jsonNodeTree);
        Yaml yaml = new Yaml();
        Map<String, Object> map = yaml.load(jsonAsYaml);
        System.out.printf("map,%s", map);
    }

    @Test
    public void t() {
        String str = "ai.csap.apidoc.web.YamlController";
        String[] strings = str.split("\\.");
        System.out.println(strings[strings.length - 1]);
        Map<String, String> map = new HashMap<>(16);
        map.put("exampleModel.age", " exampleModel.age");
        map.put("exampleModel.exampleLists", " exampleModel.age");
        map.put("exampleModel.exampleLists.name", " exampleModel.age");
        map.put("exampleModel.exampleLists.exampleListModelList2", " exampleModel.age");
        map.put("exampleModel.exampleLists.exampleListModelList2.name", " exampleModel.age");
        map.put("exampleModel.exampleLists.exampleListModelList2.exampleListModelList3.age", " exampleModel.age");
        Map<String, String> stringStringMap = map.entrySet().stream()
                .sorted(Comparator.comparingInt(i -> i.getKey().length()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (oldVal, newVal) -> oldVal, LinkedHashMap::new));
        System.out.println(map);
    }

}
