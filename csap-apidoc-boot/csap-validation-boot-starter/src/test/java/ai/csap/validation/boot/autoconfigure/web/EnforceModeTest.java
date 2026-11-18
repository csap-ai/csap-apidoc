package ai.csap.validation.boot.autoconfigure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import ai.csap.validation.boot.autoconfigure.ValidatingHandlerAdapterPostProcessor;
import ai.csap.validation.runtime.ValidatingArgumentResolver;
import ai.csap.validation.runtime.ValidationExecutor;

/**
 * enforce:规则(YAML 风格的 NotEmpty、javax / jakarta 约束注解)失败时返回 HTTP 200 + {code, message}.
 */
@SpringBootTest(classes = DemoApplication.class, properties = "csap.validation.mode=enforce")
class EnforceModeTest extends AbstractValidationMvcTest {
    @Autowired
    RequestMappingHandlerAdapter adapter;
    @Autowired
    ObjectProvider<ValidationExecutor> executor;

    @Test
    void ruleFromStrategy() throws Exception {
        listWithoutId().andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("1001"))
                .andExpect(jsonPath("$.message").value("字段id,ID不能为空"));
        mvc.perform(get("/users").param("id", "7")).andExpect(content().string("ok"));
        assertThat(recorder.violations).hasSize(1);
        assertThat(counter(DemoApplication.LIST, "NotEmpty", "enforce")).isEqualTo(1.0);
    }

    @Test
    void javaxAnnotations() throws Exception {
        create("{\"phone\":\"13800000000\"}")
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("字段username,用户名不能为空"));
        create("{\"username\":\"ab\"}")
                .andExpect(jsonPath("$.message").value("字段username,长度3-20"));
        create("{\"username\":\"abc\",\"age\":17}")
                .andExpect(jsonPath("$.message").value("字段age,未满18岁"));
        assertThat(recorder.violations).extracting(v -> v.getRule()).containsExactly("NotNull", "Size", "Min");
    }

    @Test
    void jakartaAnnotations() throws Exception {
        create("{\"username\":\"abc\",\"phone\":\"123\"}")
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("字段phone,手机号格式不对"));
        create("{\"username\":\"abc\",\"phone\":\"13800000000\",\"age\":18}")
                .andExpect(status().isOk()).andExpect(content().string("ok"));
    }

    @Test
    void onlyApiOperationMethods() throws Exception {
        mvc.perform(get("/plain")).andExpect(content().string("ok"));
    }

    @Test
    void resolversWrappedOnceInOrder() {
        List<HandlerMethodArgumentResolver> resolvers = adapter.getArgumentResolvers();
        assertThat(resolvers).isNotEmpty().allMatch(r -> r instanceof ValidatingArgumentResolver);
        // 再处理一次不会重复包装
        new ValidatingHandlerAdapterPostProcessor(executor).postProcessAfterInitialization(adapter, "again");
        assertThat(adapter.getArgumentResolvers()).isEqualTo(resolvers);
        // 原解析器顺序不变:第一个仍是 @RequestParam 解析器
        assertThat(((ValidatingArgumentResolver) resolvers.get(0)).getDelegate().getClass().getSimpleName())
                .isEqualTo("RequestParamMethodArgumentResolver");
    }
}
