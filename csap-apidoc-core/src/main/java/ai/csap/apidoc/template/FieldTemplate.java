package ai.csap.apidoc.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import ai.csap.validation.factory.Validate;

import lombok.Data;

/**
 * 字段模板:把常用字段的说明、示例、必填、校验规则存成一份,在任何接口的任何字段上套用(复制,不联动)。
 * 格式见 docs/design/1.4.0-field-library.md,Java 与 Go 共用。
 *
 * @author ycf
 * @since 1.4.0
 */
@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldTemplate {
    /**
     * 唯一标识:小写字母、数字、- _,最长 64
     */
    private String name;
    /**
     * 显示名
     */
    private String title;
    /**
     * 分类,可选
     */
    private String category;
    /**
     * 套用到字段的说明,可选
     */
    private String description;
    /**
     * 套用到字段的示例,可选
     */
    private String example;
    /**
     * 套用时是否改变必填;null 表示不改
     */
    private Boolean required;
    /**
     * 校验规则,结构与规则 JSON 的 validate 项一致
     */
    private List<Validate.ConstraintValidatorField> validate = new ArrayList<>();
    /**
     * 来源:builtin(随 jar 的内置模板)/ project(项目模板)。只在接口返回里有,不写进存储。
     */
    private String source;
}
