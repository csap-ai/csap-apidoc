package ai.csap.apidoc.web;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ai.csap.apidoc.model.CsapDocMethod;
import ai.csap.apidoc.model.CsapDocModelController;
import ai.csap.apidoc.model.CsapDocResponse;
import ai.csap.apidoc.service.ApidocContext;
import ai.csap.apidoc.service.schema.SchemaExporter;

/**
 * JSON Schema 导出端点(1.4.0)。与 /csap/openapi.json 一样只读、始终开启,返回裸 JSON(不套统一外层)。
 *
 * <ul>
 *   <li>GET /csap/schema.json - 全部接口的合集,{@code ?download=true} 时作为文件下载</li>
 *   <li>GET /csap/schema/method?key= - 单个接口的请求/返回 Schema</li>
 * </ul>
 *
 * @author ycf
 * @since 1.4.0
 */
@RestController
@RequestMapping("/csap")
public class CsapSchemaController {

    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    @GetMapping("/schema.json")
    public ResponseEntity<String> bundle(@RequestParam(required = false, defaultValue = "false") boolean download) {
        CsapDocResponse doc = ApidocContext.cmd(false, false);
        ResponseEntity.BodyBuilder b = ResponseEntity.ok().contentType(JSON_UTF8);
        if (download) {
            b.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"csap-schema.json\"");
        }
        return b.body(SchemaExporter.toJson(SchemaExporter.bundle(doc)));
    }

    @GetMapping("/schema/method")
    public ResponseEntity<String> method(@RequestParam("key") String key) {
        CsapDocResponse doc = ApidocContext.cmd(false, false);
        if (doc != null && doc.getApiList() != null) {
            for (CsapDocModelController c : doc.getApiList()) {
                if (c == null || Boolean.TRUE.equals(c.getHidden()) || c.getMethodList() == null) {
                    continue;
                }
                for (CsapDocMethod m : c.getMethodList()) {
                    if (m != null && key.equals(m.getKey()) && !Boolean.TRUE.equals(m.getHidden())) {
                        return ResponseEntity.ok().contentType(JSON_UTF8)
                                .body(SchemaExporter.toJson(SchemaExporter.method(m, c)));
                    }
                }
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(JSON_UTF8)
                .body("{\"code\":\"SCHEMA_NOT_FOUND\",\"message\":\"no documented endpoint with this key\"}");
    }
}
