package ai.csap.apidoc.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.model.YamlModel;

/**
 * @Author ycf
 * @Date 2021/9/9 4:21 下午
 * @Version 1.0
 */
@RestController
public class YamlController {

    @GetMapping("getYaml")
    public YamlModel getYaml(YamlModel yamlModel) {
        return yamlModel;
    }

    @PostMapping("addYaml")
    public YamlModel addYaml(@RequestBody YamlModel yamlModel) {
        return yamlModel;
    }
}
