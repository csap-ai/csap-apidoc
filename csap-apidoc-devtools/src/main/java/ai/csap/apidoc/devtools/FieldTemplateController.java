package ai.csap.apidoc.devtools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.template.FieldTemplate;
import ai.csap.apidoc.template.FieldTemplateService;
import ai.csap.apidoc.util.RuleStorageNotWritableException;
import ai.csap.apidoc.util.ValidateException;

/**
 * 字段模板:内置 + 项目模板的列表、保存、删除。套用本身是前端把模板内容写进字段,走现有的保存字段规则接口。
 * 不可写(打包运行、只读模式)时返回 {@code code = 1007}。
 *
 * @author ycf
 * @since 1.4.0
 */
@RequestMapping("csap/yaml/fieldTemplates")
@RestController
public class FieldTemplateController {

    private final FieldTemplateService service;

    public FieldTemplateController(EnableApidocConfig enableApidocConfig) {
        this(new FieldTemplateService(enableApidocConfig));
    }

    public FieldTemplateController(FieldTemplateService service) {
        this.service = service;
    }

    /**
     * 内置 + 项目模板(每个带 source),以及是否支持保存项目模板
     */
    @GetMapping
    public ApidocResult<Map<String, Object>> list() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectSupported", service.projectSupported());
        List<FieldTemplate> templates = service.list();
        m.put("templates", templates);
        return ApidocResult.success(m);
    }

    /**
     * 新增或修改一个项目模板
     */
    @PostMapping
    public ApidocResult<Boolean> save(@RequestBody FieldTemplate template) {
        service.saveProject(template);
        return ApidocResult.success(Boolean.TRUE);
    }

    /**
     * 删除一个项目模板(内置模板不能删除)
     */
    @DeleteMapping("{name}")
    public ApidocResult<Boolean> delete(@PathVariable String name) {
        return ApidocResult.success(service.deleteProject(name));
    }

    @ExceptionHandler(RuleStorageNotWritableException.class)
    public ApidocResult<Object> notWritable(RuleStorageNotWritableException e) {
        return ApidocResult.error(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(ValidateException.class)
    public ApidocResult<Object> invalid(ValidateException e) {
        return ApidocResult.error(String.valueOf(e.getCode()), e.getMessage());
    }
}
