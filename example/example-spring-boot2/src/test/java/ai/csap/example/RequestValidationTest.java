package ai.csap.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

import ai.csap.apidoc.autoconfigure.RunApplicationListener;
import ai.csap.apidoc.service.ApidocContext;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Spring Boot 2.7 + Hibernate Validator 6(javax.validation):运行时校验端到端.
 *
 * <p>规则由文档扫描登记:User 上的 javax 注解,以及测试资源中的 UserController-method-field.yaml。
 * 启动时的异步扫描被替换掉,测试里同步扫描一次,避免扫描与请求并发。
 */
@SpringBootTest(properties = {
        "csap.validation.mode=enforce",
        "csap.validation.endpoints[ai.csap.example.controller.UserController.updateUser]=observe"})
@AutoConfigureMockMvc
class RequestValidationTest {
    @MockBean
    RunApplicationListener runApplicationListener;
    @Autowired
    MockMvc mvc;
    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void scanDocs() {
        ApidocContext.cmd(true, false);
    }

    @Test
    void javaxAnnotationsAreEnforced() throws Exception {
        postJson("/api/users", "{\"username\":\"ab\",\"email\":\"a@b.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("字段username,用户名长度必须在3-50之间"));
        postJson("/api/users", "{\"username\":\"john\",\"email\":\"not-an-email\"}")
                .andExpect(jsonPath("$.message").value("字段email,邮箱格式不正确"));
        postJson("/api/users", "{\"username\":\"john\",\"email\":\"john@example.com\"}")
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("john"));
    }

    @Test
    void yamlRulesAreEnforced() throws Exception {
        mvc.perform(get("/api/users").param("keyword", "ABC"))
                .andExpect(jsonPath("$.code").value("1002"))
                .andExpect(jsonPath("$.message").value("字段keyword,只能是小写字母"));
        mvc.perform(get("/api/users").param("keyword", "abc"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void endpointOverrideObserves() throws Exception {
        mvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ab\",\"email\":\"a@b.com\"}"))
                .andExpect(jsonPath("$.code").value(200));
        assertThat(meterRegistry.find("csap.validation.violations")
                .tag("endpoint", "ai.csap.example.controller.UserController.updateUser")
                .tag("mode", "observe").counter()).isNotNull();
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
