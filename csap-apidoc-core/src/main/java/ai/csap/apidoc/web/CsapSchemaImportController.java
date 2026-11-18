package ai.csap.apidoc.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.core.ApidocResult;
import ai.csap.apidoc.service.schema.SchemaImportService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON Schema 导入端点 POST /csap/schema/import。默认关闭(csap.apidoc.schema.import-enabled=true 才注册):
 * 它会写字段规则,导入的校验在运行时生效。
 *
 * <p>请求体(JSON 文本):{@code {"key","kind":"request|response","section","schema","apply":false}};
 * 默认只预览,{@code apply:true} 才写入。
 *
 * @author ycf
 * @since 1.4.0
 */
@RestController
@RequestMapping("/csap")
public class CsapSchemaImportController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SchemaImportService service;

    public CsapSchemaImportController(SchemaImportService service) {
        this.service = service;
    }

    @PostMapping("/schema/import")
    public ApidocResult<Object> importSchema(@RequestBody String body) {
        try {
            JsonNode req = MAPPER.readTree(body);
            String key = req.path("key").asText("");
            String kind = req.path("kind").asText("");
            if (key.isEmpty() || !("request".equals(kind) || "response".equals(kind))) {
                return ApidocResult.error("400", "key 必填,kind 只能是 request 或 response");
            }
            String section = req.path("section").isTextual() ? req.get("section").asText() : null;
            Object data = service.run(key, "request".equals(kind), section, req.get("schema"),
                    req.path("apply").asBoolean(false));
            return ApidocResult.success(data);
        } catch (IllegalArgumentException e) {
            return ApidocResult.error("400", e.getMessage());
        } catch (java.io.IOException e) {
            return ApidocResult.error("400", "请求体不是合法的 JSON: " + e.getMessage());
        } catch (RuntimeException e) {
            return ApidocResult.error("500", e.getMessage());
        }
    }
}
