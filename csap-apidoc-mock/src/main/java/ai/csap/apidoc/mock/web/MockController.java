package ai.csap.apidoc.mock.web;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.http.server.PathContainer;

import ai.csap.apidoc.mock.MockProperties;
import ai.csap.apidoc.mock.MockService;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mock 端点 /csap/mock/**(任意 HTTP 方法)和 /csap/mock-status。
 * 只在 csap.apidoc.mock.enabled=true 时由自动配置注册;不引用 Servlet 类,Spring Boot 2 / 3 通用。
 *
 * @author ycf
 * @since 1.4.0
 */
@ConditionalOnProperty(prefix = MockProperties.PREFIX, name = "enabled", havingValue = "true")
@RestController
public class MockController {

    static final String SEED_HEADER = "X-CSAP-Mock-Seed";
    static final String SEED_PARAM = "_csap_mock_seed";
    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockService service;
    private final MockProperties properties;

    public MockController(MockService service, MockProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping(MockProperties.BASE_PATH + "/**")
    public ResponseEntity<String> get(NativeWebRequest request) {
        return handle("GET", request);
    }

    @PostMapping(MockProperties.BASE_PATH + "/**")
    public ResponseEntity<String> post(NativeWebRequest request) {
        return handle("POST", request);
    }

    @PutMapping(MockProperties.BASE_PATH + "/**")
    public ResponseEntity<String> put(NativeWebRequest request) {
        return handle("PUT", request);
    }

    @DeleteMapping(MockProperties.BASE_PATH + "/**")
    public ResponseEntity<String> delete(NativeWebRequest request) {
        return handle("DELETE", request);
    }

    @PatchMapping(MockProperties.BASE_PATH + "/**")
    public ResponseEntity<String> patch(NativeWebRequest request) {
        return handle("PATCH", request);
    }

    /**
     * 文档页探测 Mock 是否开启。开启时 200;未开启时这个端点不存在(404)。
     * 返回裸 JSON:{"enabled":true,"prefix":"/csap/mock","seed":"csap"}
     */
    @RequestMapping(value = "/csap/mock-status", method = org.springframework.web.bind.annotation.RequestMethod.GET)
    public ResponseEntity<String> status() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", true);
        m.put("prefix", MockProperties.BASE_PATH);
        m.put("seed", properties.getSeed());
        return ResponseEntity.ok().contentType(JSON_UTF8).body(MAPPER.writeValueAsString(m));
    }

    private ResponseEntity<String> handle(String verb, NativeWebRequest request) {
        String within = pathWithinApplication(request);
        String path = within.startsWith(MockProperties.BASE_PATH)
                ? within.substring(MockProperties.BASE_PATH.length()) : within;
        String seed = request.getHeader(SEED_HEADER);
        if (seed == null || seed.isEmpty()) {
            seed = request.getParameter(SEED_PARAM);
        }
        MockService.Reply reply = service.handle(verb, path, seed);
        ResponseEntity.BodyBuilder b = ResponseEntity.status(reply.getStatus());
        HttpHeaders headers = new HttpHeaders();
        reply.getHeaders().forEach(headers::set);
        b.headers(headers);
        if (reply.getBody() == null) {
            return b.build();
        }
        return b.contentType(JSON_UTF8).body(reply.getBody());
    }

    private static String pathWithinApplication(NativeWebRequest request) {
        Object attr = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        if (attr instanceof PathContainer) {
            return ((PathContainer) attr).value();
        }
        return attr == null ? "" : attr.toString();
    }
}
