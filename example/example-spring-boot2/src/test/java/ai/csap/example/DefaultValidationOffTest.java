package ai.csap.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ai.csap.apidoc.autoconfigure.RunApplicationListener;

/**
 * 引入 csap-validation-boot-starter 的 Spring Boot 2.7 应用能正常启动(1.1.x 因缺少 jakarta.validation 类启动失败),
 * 不配置时不做运行时校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
class DefaultValidationOffTest {
    @MockBean
    RunApplicationListener runApplicationListener;
    @Autowired
    MockMvc mvc;

    @Test
    void startsAndDoesNotValidate() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ab\"}"))
                .andExpect(jsonPath("$.code").value(200));
    }
}
