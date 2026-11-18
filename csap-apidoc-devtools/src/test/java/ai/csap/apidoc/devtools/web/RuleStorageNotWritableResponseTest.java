package ai.csap.apidoc.devtools.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ai.csap.apidoc.autoconfigure.EnableApidocConfig;
import ai.csap.apidoc.devtools.ApidocStrategyController;
import ai.csap.apidoc.devtools.core.ApidocDevtools;
import ai.csap.apidoc.devtools.model.api.MethodModel;
import ai.csap.apidoc.util.RuleStorageNotWritableException;

/**
 * 规则存储不可写(以 jar 运行时 YAML / JSON 规则文件在 jar 内)时,DevTools 保存接口返回 HTTP 200 +
 * ApidocResult 失败结构(code=1007,message 说明可用的做法),前端可直接显示,而不是 500。
 */
class RuleStorageNotWritableResponseTest {

    @Test
    void saveRuleReturnsClearErrorInsteadOf500() throws Exception {
        ApidocDevtools devtools = mock(ApidocDevtools.class);
        when(devtools.addMethodParam(any(MethodModel.class), anyBoolean()))
                .thenThrow(RuleStorageNotWritableException.classpathNotWritable("YAML", null));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApidocStrategyController(new EnableApidocConfig(), devtools))
                .build();

        String body = mvc.perform(post("/csap/yaml/addResponseParam").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"className\":\"a.B\",\"methodName\":\"m\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1007"))
                .andExpect(jsonPath("$.success").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("YAML 规则文件位于 jar 内", "SQL_LITE", "1.4.0", "ApiStrategyType.DATABASE", "Redis 模式");
    }
}
