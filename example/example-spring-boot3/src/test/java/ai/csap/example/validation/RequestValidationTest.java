package ai.csap.example.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import ai.csap.apidoc.autoconfigure.RunApplicationListener;
import ai.csap.apidoc.service.ApidocContext;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Spring Boot 3 + Hibernate Validator 8(jakarta.validation):运行时校验端到端.
 *
 * <p>启动时的异步扫描被替换掉,测试里同步扫描一次,避免扫描与请求并发。
 */
@SpringBootTest(classes = ValidationTestApplication.class, properties = {
        "csap.validation.mode=observe",
        "csap.validation.endpoints[ai.csap.example.validation.AccountController.createAccount]=enforce",
        "csap.validation.endpoints[ai.csap.example.controller.ProductController.updateStock]=enforce",
        "csap.validation.http-status=400"})
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
    void jakartaAnnotationsAreEnforced() throws Exception {
        postJson("{\"username\":\"ab\",\"email\":\"a@b.com\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("字段username,用户名长度必须在3-20之间"));
        postJson("{\"username\":\"john\",\"email\":\"bad\"}")
                .andExpect(jsonPath("$.message").value("字段email,邮箱格式不正确"));
        // YAML 规则(level 0)先于注解规则执行
        postJson("{\"username\":\"john\"}")
                .andExpect(jsonPath("$.code").value("1001"))
                .andExpect(jsonPath("$.message").value("字段email,邮箱不能为空"));
        postJson("{\"username\":\"john\",\"email\":\"john@example.com\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("john"));
        assertThat(meterRegistry.find("csap.validation.violations")
                .tag("endpoint", "ai.csap.example.validation.AccountController.createAccount")
                .tag("rule", "Size").tag("mode", "enforce").counter().count()).isEqualTo(1.0);
    }

    @Test
    void yamlRuleOnInterfaceAnnotatedMethod() throws Exception {
        mvc.perform(patch("/api/products/updateStock/1/stock").param("stock", "10000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("1002"))
                .andExpect(jsonPath("$.message").value("字段stock,库存只能是0-9999"));
        mvc.perform(patch("/api/products/updateStock/1/stock").param("stock", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(99));
    }

    private ResultActions postJson(String json) throws Exception {
        return mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
