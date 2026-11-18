package ai.csap.apidoc.template;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * 字段模板文件(YAML / JSON):{@code {version: 1, templates: [...]}}
 *
 * @author ycf
 * @since 1.4.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldTemplateFile {
    public static final int VERSION = 1;

    private int version = VERSION;
    private List<FieldTemplate> templates = new ArrayList<>();
}
